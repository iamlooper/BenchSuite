use std::sync::atomic::{AtomicBool, Ordering};
use std::ffi::CString;
use libc::{
    CLOCK_MONOTONIC, MAP_SHARED, MS_SYNC, O_CREAT, O_RDONLY, O_RDWR, O_TRUNC,
    PROT_READ, PROT_WRITE, SEEK_SET, off_t, stat, timespec,
};

use crate::benchmarks::{
    Benchmark, BenchConfig, BenchmarkConfig, BenchResult, Category, MetricUnit, emit_sample,
};
use crate::ring_buffer::RingBufferWriter;
use std::mem::zeroed;
use std::ptr::null_mut;

macro_rules! time_ns {
    ($body:block) => {{
        let mut t0 = timespec { tv_sec: 0, tv_nsec: 0 };
        let mut t1 = timespec { tv_sec: 0, tv_nsec: 0 };
        unsafe { libc::clock_gettime(CLOCK_MONOTONIC, &mut t0) };
        $body
        unsafe { libc::clock_gettime(CLOCK_MONOTONIC, &mut t1) };
        (t1.tv_sec - t0.tv_sec) as u64 * 1_000_000_000 + (t1.tv_nsec - t0.tv_nsec) as u64
    }};
}

const FILE_SIZE: usize = 64 * 1024 * 1024;   // 64 MB test file
const BLOCK:     usize = 4096;

fn test_file_path(storage_path: &str) -> String {
    format!("{}/.benchsuite_io_test", storage_path)
}

fn open_test_file(storage_path: &str, write: bool) -> i32 {
    let path = CString::new(test_file_path(storage_path)).unwrap();
    let flags = if write { O_RDWR | O_CREAT | O_TRUNC } else { O_RDWR };
    let fd = unsafe { libc::open(path.as_ptr(), flags, 0o600) };
    if fd < 0 && write {
        // Try parent is writable at all - return invalid fd, caller skips gracefully
        return -1;
    }
    if fd >= 0 && write {
        unsafe { libc::ftruncate(fd, FILE_SIZE as off_t) };
        // Pre-populate so reads don't get zero pages
        let buf = vec![0x5Au8; FILE_SIZE];
        unsafe { libc::write(fd, buf.as_ptr() as _, FILE_SIZE) };
        unsafe { libc::fsync(fd) };
        unsafe { libc::lseek(fd, 0, SEEK_SET) };
    }
    fd
}

fn unlink_test_file(storage_path: &str) {
    let path = CString::new(test_file_path(storage_path)).unwrap();
    unsafe { libc::unlink(path.as_ptr()) };
}

// --- Sequential write ---

pub struct SeqWrite;

impl Benchmark for SeqWrite {
    fn id(&self)       -> &'static str { "io.seq_write" }
    fn name(&self)     -> &'static str { "Sequential write throughput" }
    fn category(&self) -> Category     { Category::Io }
    fn config(&self)   -> BenchmarkConfig {
        BenchmarkConfig { warmup_iters: 3, measure_iters: 20, unit: MetricUnit::MbPerSec }
    }

    fn run(&self, writer: Option<&RingBufferWriter>, cancel: &AtomicBool, bench_cfg: &BenchConfig) -> BenchResult {
        let cfg = self.config();
        let bench_id = 22u16;
        let mut samples = Vec::with_capacity(cfg.measure_iters as usize);
        let buf = vec![0xA5u8; FILE_SIZE];

        for i in 0..cfg.measure_iters {
            if cancel.load(Ordering::Acquire) { break; }
            let fd = open_test_file(&bench_cfg.storage_path, true);
            if fd < 0 { break; }
            let elapsed = time_ns!({
                let mut pos = 0usize;
                while pos < FILE_SIZE {
                    let n = unsafe { libc::write(fd, buf.as_ptr().add(pos) as _, BLOCK) };
                    if n <= 0 { break; }
                    pos += n as usize;
                }
                unsafe { libc::fsync(fd) };
            });
            unsafe { libc::close(fd) };
            if elapsed == 0 { continue; }
            let mbps = FILE_SIZE as f64 / (elapsed as f64 / 1e9) / (1024.0 * 1024.0);
            samples.push(mbps);
            emit_sample(writer, bench_id, Category::Io, 1, i, cfg.measure_iters, mbps, MetricUnit::MbPerSec, 0);
        }

        unlink_test_file(&bench_cfg.storage_path);
        BenchResult::from_samples(samples, MetricUnit::MbPerSec)
    }
}

// --- Sequential read ---

pub struct SeqRead;

impl Benchmark for SeqRead {
    fn id(&self)       -> &'static str { "io.seq_read" }
    fn name(&self)     -> &'static str { "Sequential read throughput" }
    fn category(&self) -> Category     { Category::Io }
    fn config(&self)   -> BenchmarkConfig {
        BenchmarkConfig { warmup_iters: 3, measure_iters: 20, unit: MetricUnit::MbPerSec }
    }

    fn run(&self, writer: Option<&RingBufferWriter>, cancel: &AtomicBool, bench_cfg: &BenchConfig) -> BenchResult {
        let cfg = self.config();
        let bench_id = 23u16;
        let mut samples = Vec::with_capacity(cfg.measure_iters as usize);

        let fd = open_test_file(&bench_cfg.storage_path, true);
        if fd < 0 { return BenchResult::default(); }

        for i in 0..cfg.measure_iters {
            if cancel.load(Ordering::Acquire) { break; }
            unsafe { libc::lseek(fd, 0, SEEK_SET) };
            let mut buf = vec![0u8; BLOCK];
            let elapsed = time_ns!({
                let mut pos = 0usize;
                while pos < FILE_SIZE {
                    let n = unsafe { libc::read(fd, buf.as_mut_ptr() as _, BLOCK) };
                    if n <= 0 { break; }
                    pos += n as usize;
                }
            });
            if elapsed == 0 { continue; }
            let mbps = FILE_SIZE as f64 / (elapsed as f64 / 1e9) / (1024.0 * 1024.0);
            samples.push(mbps);
            emit_sample(writer, bench_id, Category::Io, 1, i, cfg.measure_iters, mbps, MetricUnit::MbPerSec, 0);
        }

        unsafe { libc::close(fd) };
        unlink_test_file(&bench_cfg.storage_path);
        BenchResult::from_samples(samples, MetricUnit::MbPerSec)
    }
}

// --- Random write (4kb blocks) ---

pub struct RandWrite;

impl Benchmark for RandWrite {
    fn id(&self)       -> &'static str { "io.rand_write" }
    fn name(&self)     -> &'static str { "Random write (4KB blocks)" }
    fn category(&self) -> Category     { Category::Io }
    fn config(&self)   -> BenchmarkConfig {
        BenchmarkConfig { warmup_iters: 100, measure_iters: 2_000, unit: MetricUnit::UsPerOp }
    }

    fn run(&self, writer: Option<&RingBufferWriter>, cancel: &AtomicBool, bench_cfg: &BenchConfig) -> BenchResult {
        let cfg = self.config();
        let bench_id = 24u16;
        let mut samples = Vec::with_capacity(cfg.measure_iters as usize);
        let n_blocks = (FILE_SIZE / BLOCK) as u64;
        let buf = vec![0xCCu8; BLOCK];
        let fd = open_test_file(&bench_cfg.storage_path, true);
        if fd < 0 { return BenchResult::default(); }

        let mut rng = simple_rng(12345);
        for _ in 0..cfg.warmup_iters {
            let block = lcg_next(&mut rng) % n_blocks;
            unsafe {
                libc::lseek(fd, (block * BLOCK as u64) as off_t, SEEK_SET);
                libc::write(fd, buf.as_ptr() as _, BLOCK);
            }
        }

        for i in 0..cfg.measure_iters {
            if i % 200 == 0 && cancel.load(Ordering::Acquire) { break; }
            let block = lcg_next(&mut rng) % n_blocks;
            let elapsed = time_ns!({
                unsafe {
                    libc::lseek(fd, (block * BLOCK as u64) as off_t, SEEK_SET);
                    libc::write(fd, buf.as_ptr() as _, BLOCK);
                    libc::fsync(fd);
                }
            });
            let us = elapsed as f64 / 1000.0;
            samples.push(us);
            emit_sample(writer, bench_id, Category::Io, 1, i, cfg.measure_iters, us, MetricUnit::UsPerOp, 0);
        }

        unsafe { libc::close(fd) };
        unlink_test_file(&bench_cfg.storage_path);
        BenchResult::from_samples(samples, MetricUnit::UsPerOp)
    }
}

// --- Random read (4kb blocks) ---

pub struct RandRead;

impl Benchmark for RandRead {
    fn id(&self)       -> &'static str { "io.rand_read" }
    fn name(&self)     -> &'static str { "Random read (4KB blocks)" }
    fn category(&self) -> Category     { Category::Io }
    fn config(&self)   -> BenchmarkConfig {
        BenchmarkConfig { warmup_iters: 100, measure_iters: 2_000, unit: MetricUnit::UsPerOp }
    }

    fn run(&self, writer: Option<&RingBufferWriter>, cancel: &AtomicBool, bench_cfg: &BenchConfig) -> BenchResult {
        let cfg = self.config();
        let bench_id = 25u16;
        let n_blocks = (FILE_SIZE / BLOCK) as u64;
        let mut samples = Vec::with_capacity(cfg.measure_iters as usize);
        let fd = open_test_file(&bench_cfg.storage_path, true);
        if fd < 0 { return BenchResult::default(); }

        let mut rng = simple_rng(67890);
        for _ in 0..cfg.warmup_iters {
            let block = lcg_next(&mut rng) % n_blocks;
            let mut buf = [0u8; BLOCK];
            unsafe {
                libc::lseek(fd, (block * BLOCK as u64) as off_t, SEEK_SET);
                libc::read(fd, buf.as_mut_ptr() as _, BLOCK);
            }
        }

        for i in 0..cfg.measure_iters {
            if i % 200 == 0 && cancel.load(Ordering::Acquire) { break; }
            let block = lcg_next(&mut rng) % n_blocks;
            let mut buf = [0u8; BLOCK];
            let elapsed = time_ns!({
                unsafe {
                    libc::lseek(fd, (block * BLOCK as u64) as off_t, SEEK_SET);
                    libc::read(fd, buf.as_mut_ptr() as _, BLOCK);
                }
            });
            let us = elapsed as f64 / 1000.0;
            samples.push(us);
            emit_sample(writer, bench_id, Category::Io, 1, i, cfg.measure_iters, us, MetricUnit::UsPerOp, 0);
        }

        unsafe { libc::close(fd) };
        unlink_test_file(&bench_cfg.storage_path);
        BenchResult::from_samples(samples, MetricUnit::UsPerOp)
    }
}

// --- mmap file R/W ---

pub struct MmapFileRw;

impl Benchmark for MmapFileRw {
    fn id(&self)       -> &'static str { "io.mmap_file_rw" }
    fn name(&self)     -> &'static str { "mmap file sequential R/W" }
    fn category(&self) -> Category     { Category::Io }
    fn config(&self)   -> BenchmarkConfig {
        BenchmarkConfig { warmup_iters: 3, measure_iters: 20, unit: MetricUnit::MbPerSec }
    }

    fn run(&self, writer: Option<&RingBufferWriter>, cancel: &AtomicBool, bench_cfg: &BenchConfig) -> BenchResult {
        let cfg = self.config();
        let bench_id = 26u16;
        let mut samples = Vec::with_capacity(cfg.measure_iters as usize);
        let fd = open_test_file(&bench_cfg.storage_path, true);
        if fd < 0 { return BenchResult::default(); }

        for i in 0..cfg.measure_iters {
            if cancel.load(Ordering::Acquire) { break; }
            let elapsed = time_ns!({
                unsafe {
                    let ptr = libc::mmap(
                        null_mut(), FILE_SIZE,
                        PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0,
                    );
                    // Touch every 4KB page (sequential)
                    let mut i = 0usize;
                    while i < FILE_SIZE {
                        (ptr as *mut u8).add(i).write_volatile(0xEE);
                        i += BLOCK;
                    }
                    libc::msync(ptr, FILE_SIZE, MS_SYNC);
                    libc::munmap(ptr, FILE_SIZE);
                }
            });
            if elapsed == 0 { continue; }
            let mbps = FILE_SIZE as f64 / (elapsed as f64 / 1e9) / (1024.0 * 1024.0);
            samples.push(mbps);
            emit_sample(writer, bench_id, Category::Io, 1, i, cfg.measure_iters, mbps, MetricUnit::MbPerSec, 0);
        }

        unsafe { libc::close(fd) };
        unlink_test_file(&bench_cfg.storage_path);
        BenchResult::from_samples(samples, MetricUnit::MbPerSec)
    }
}

// --- Metadata ops (open/close/stat) ---

pub struct MetadataOps;

impl Benchmark for MetadataOps {
    fn id(&self)       -> &'static str { "io.metadata_ops" }
    fn name(&self)     -> &'static str { "Metadata ops (open/fstat/close)" }
    fn category(&self) -> Category     { Category::Io }
    fn config(&self)   -> BenchmarkConfig {
        BenchmarkConfig { warmup_iters: 200, measure_iters: 5_000, unit: MetricUnit::UsPerOp }
    }

    fn run(&self, writer: Option<&RingBufferWriter>, cancel: &AtomicBool, bench_cfg: &BenchConfig) -> BenchResult {
        let cfg = self.config();
        let bench_id = 27u16;
        let mut samples = Vec::with_capacity(cfg.measure_iters as usize);
        let path = CString::new(test_file_path(&bench_cfg.storage_path)).unwrap();

        // Create a small file for the metadata test
        let fd_init = unsafe { libc::open(path.as_ptr(), O_RDWR | O_CREAT | O_TRUNC, 0o600) };
        if fd_init < 0 { return BenchResult::default(); }
        unsafe { libc::close(fd_init) };

        for _ in 0..cfg.warmup_iters {
            unsafe {
                let fd = libc::open(path.as_ptr(), O_RDONLY, 0);
                let mut st: stat = zeroed();
                libc::fstat(fd, &mut st);
                libc::close(fd);
            }
        }

        for i in 0..cfg.measure_iters {
            if i % 500 == 0 && cancel.load(Ordering::Acquire) { break; }
            let elapsed = time_ns!({
                unsafe {
                    let fd = libc::open(path.as_ptr(), O_RDONLY, 0);
                    let mut st: stat = zeroed();
                    libc::fstat(fd, &mut st);
                    libc::close(fd);
                }
            });
            let us = elapsed as f64 / 1000.0;
            samples.push(us);
            emit_sample(writer, bench_id, Category::Io, 1, i, cfg.measure_iters, us, MetricUnit::UsPerOp, 0);
        }

        unsafe { libc::unlink(path.as_ptr()) };
        BenchResult::from_samples(samples, MetricUnit::UsPerOp)
    }
}

// --- Simple inline LCG RNG (no rand crate dep for this module) ---

fn simple_rng(seed: u64) -> u64 { seed }
fn lcg_next(state: &mut u64) -> u64 {
    *state = state.wrapping_mul(6364136223846793005).wrapping_add(1442695040888963407);
    *state
}
