use std::sync::atomic::{AtomicBool, Ordering};
use libc::{
    CLOCK_MONOTONIC, MAP_ANON, MAP_PRIVATE, MAP_SHARED, MS_SYNC,
    O_CREAT, O_RDWR, O_TRUNC, PROT_READ, PROT_WRITE, _SC_PAGESIZE, off_t, size_t, timespec,
};

use crate::benchmarks::{
    Benchmark, BenchConfig, BenchmarkConfig, BenchResult, Category, MetricUnit, emit_sample,
};
use crate::ring_buffer::RingBufferWriter;
use std::hint::black_box;
use std::ptr::{null_mut, write_volatile};

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

// --- mmap anonymous fault benchmark ---
// Maps a fresh anonymous page, touches it (page fault), then unmaps.

pub struct MmapAnonymousFault;

impl Benchmark for MmapAnonymousFault {
    fn id(&self)       -> &'static str { "memory.mmap_anon_fault" }
    fn name(&self)     -> &'static str { "mmap+fault (anonymous)" }
    fn category(&self) -> Category     { Category::Memory }
    fn config(&self)   -> BenchmarkConfig {
        BenchmarkConfig { warmup_iters: 50, measure_iters: 1_000, unit: MetricUnit::UsPerOp }
    }

    fn run(&self, writer: Option<&RingBufferWriter>, cancel: &AtomicBool, _cfg: &BenchConfig) -> BenchResult {
        let cfg = self.config();
        let bench_id = 5u16;
        let page = unsafe { libc::sysconf(_SC_PAGESIZE) as usize };
        let mut samples = Vec::with_capacity(cfg.measure_iters as usize);

        for _ in 0..cfg.warmup_iters {
            unsafe {
                let p = libc::mmap(null_mut(), page, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANON, -1, 0);
                write_volatile(p as *mut u8, 42);
                libc::munmap(p, page);
            }
        }

        for i in 0..cfg.measure_iters {
            if i % 100 == 0 && cancel.load(Ordering::Acquire) { break; }
            let elapsed = time_ns!({
                unsafe {
                    let p = libc::mmap(null_mut(), page, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANON, -1, 0);
                    write_volatile(p as *mut u8, 42);
                    libc::munmap(p, page);
                }
            });
            let us = elapsed as f64 / 1000.0;
            samples.push(us);
            emit_sample(writer, bench_id, Category::Memory, 1, i, cfg.measure_iters, us, MetricUnit::UsPerOp, 0);
        }

        BenchResult::from_samples(samples, MetricUnit::UsPerOp)
    }
}

// --- mmap+munmap cycle benchmark ---

pub struct MmapCycle;

impl Benchmark for MmapCycle {
    fn id(&self)       -> &'static str { "memory.mmap_cycle" }
    fn name(&self)     -> &'static str { "mmap+munmap round-trip" }
    fn category(&self) -> Category     { Category::Memory }
    fn config(&self)   -> BenchmarkConfig {
        BenchmarkConfig { warmup_iters: 50, measure_iters: 2_000, unit: MetricUnit::UsPerOp }
    }

    fn run(&self, writer: Option<&RingBufferWriter>, cancel: &AtomicBool, _cfg: &BenchConfig) -> BenchResult {
        let cfg = self.config();
        let bench_id = 6u16;
        let size: usize = 4096 * 16;  // 64 KB
        let mut samples = Vec::with_capacity(cfg.measure_iters as usize);

        for _ in 0..cfg.warmup_iters {
            unsafe {
                let p = libc::mmap(null_mut(), size, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANON, -1, 0);
                libc::munmap(p, size);
            }
        }

        for i in 0..cfg.measure_iters {
            if i % 200 == 0 && cancel.load(Ordering::Acquire) { break; }
            let elapsed = time_ns!({
                unsafe {
                    let p = libc::mmap(null_mut(), size, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANON, -1, 0);
                    libc::munmap(p, size);
                }
            });
            let us = elapsed as f64 / 1000.0;
            samples.push(us);
            emit_sample(writer, bench_id, Category::Memory, 1, i, cfg.measure_iters, us, MetricUnit::UsPerOp, 0);
        }

        BenchResult::from_samples(samples, MetricUnit::UsPerOp)
    }
}

// --- memcpy bandwidth benchmark ---

pub struct MemcpyBandwidth;

impl Benchmark for MemcpyBandwidth {
    fn id(&self)       -> &'static str { "memory.memcpy_bandwidth" }
    fn name(&self)     -> &'static str { "memcpy() bandwidth" }
    fn category(&self) -> Category     { Category::Memory }
    fn config(&self)   -> BenchmarkConfig {
        BenchmarkConfig { warmup_iters: 10, measure_iters: 200, unit: MetricUnit::GbPerSec }
    }

    fn run(&self, writer: Option<&RingBufferWriter>, cancel: &AtomicBool, _cfg: &BenchConfig) -> BenchResult {
        let cfg = self.config();
        let bench_id = 7u16;
        let size: usize = 64 * 1024 * 1024;  // 64 MB transfer per iteration
        let src: Vec<u8> = vec![0xABu8; size];
        let mut dst: Vec<u8> = vec![0u8;    size];
        let mut samples = Vec::with_capacity(cfg.measure_iters as usize);

        for _ in 0..cfg.warmup_iters {
            unsafe { libc::memcpy(dst.as_mut_ptr() as _, src.as_ptr() as _, size) };
        }

        for i in 0..cfg.measure_iters {
            if i % 20 == 0 && cancel.load(Ordering::Acquire) { break; }
            let elapsed = time_ns!({
                unsafe { libc::memcpy(dst.as_mut_ptr() as _, src.as_ptr() as _, size) };
            });
            // Skip this sample if clock_gettime returned identical timestamps.
            if elapsed == 0 { continue; }
            // GB/s = bytes / ns
            let gbps = size as f64 / elapsed as f64;
            samples.push(gbps);
            emit_sample(writer, bench_id, Category::Memory, 1, i, cfg.measure_iters, gbps, MetricUnit::GbPerSec, 0);
        }

        BenchResult::from_samples(samples, MetricUnit::GbPerSec)
    }
}

// --- Stride-sweep benchmark (cache hierarchy exploration) ---

pub struct StrideSweep;

impl Benchmark for StrideSweep {
    fn id(&self)       -> &'static str { "memory.stride_sweep" }
    fn name(&self)     -> &'static str { "Stride-sweep (cache latency)" }
    fn category(&self) -> Category     { Category::Memory }
    fn config(&self)   -> BenchmarkConfig {
        BenchmarkConfig { warmup_iters: 3, measure_iters: 20, unit: MetricUnit::NsPerOp }
    }

    fn run(&self, writer: Option<&RingBufferWriter>, cancel: &AtomicBool, _cfg: &BenchConfig) -> BenchResult {
        let cfg = self.config();
        let bench_id = 8u16;
        // 128 MB buffer - forces main memory accesses during stride sweep
        let size: usize = 128 * 1024 * 1024;
        let mut buf: Vec<u64> = vec![0u64; size / 8];
        // Build pointer chain at 256-byte stride (> cache line)
        let stride = 256 / 8;
        let slots = buf.len() / stride;
        for i in 0..slots {
            buf[i * stride] = ((i + 1) % slots * stride) as u64;
        }
        let mut samples = Vec::with_capacity(cfg.measure_iters as usize);
        let accesses: u64 = 10_000_000;

        for _ in 0..cfg.warmup_iters {
            let mut ptr = 0usize;
            for _ in 0..accesses { ptr = buf[ptr] as usize; }
            black_box(ptr);
        }

        for i in 0..cfg.measure_iters {
            if cancel.load(Ordering::Acquire) { break; }
            let elapsed = time_ns!({
                let mut ptr = 0usize;
                for _ in 0..accesses { ptr = buf[ptr] as usize; }
                black_box(ptr);
            });
            let ns = elapsed as f64 / accesses as f64;
            samples.push(ns);
            emit_sample(writer, bench_id, Category::Memory, 1, i, cfg.measure_iters, ns, MetricUnit::NsPerOp, 0);
        }

        BenchResult::from_samples(samples, MetricUnit::NsPerOp)
    }
}

// --- malloc/free benchmark ---

pub struct MallocFree;

impl Benchmark for MallocFree {
    fn id(&self)       -> &'static str { "memory.malloc_free" }
    fn name(&self)     -> &'static str { "malloc()+free() latency" }
    fn category(&self) -> Category     { Category::Memory }
    fn config(&self)   -> BenchmarkConfig {
        BenchmarkConfig { warmup_iters: 500, measure_iters: 10_000, unit: MetricUnit::NsPerOp }
    }

    fn run(&self, writer: Option<&RingBufferWriter>, cancel: &AtomicBool, _cfg: &BenchConfig) -> BenchResult {
        let cfg = self.config();
        let bench_id = 9u16;
        let alloc_size: size_t = 256;
        let mut samples = Vec::with_capacity(cfg.measure_iters as usize);

        for _ in 0..cfg.warmup_iters {
            unsafe { libc::free(libc::malloc(alloc_size)); }
        }

        for i in 0..cfg.measure_iters {
            if i % 1000 == 0 && cancel.load(Ordering::Acquire) { break; }
            let elapsed = time_ns!({
                unsafe { libc::free(libc::malloc(alloc_size)); }
            });
            let ns = elapsed as f64;
            samples.push(ns);
            emit_sample(writer, bench_id, Category::Memory, 1, i, cfg.measure_iters, ns, MetricUnit::NsPerOp, 0);
        }

        BenchResult::from_samples(samples, MetricUnit::NsPerOp)
    }
}

// --- File-backed mmap R/W ---

pub struct FileBackedMmap;

impl Benchmark for FileBackedMmap {
    fn id(&self)       -> &'static str { "memory.file_backed_mmap" }
    fn name(&self)     -> &'static str { "File-backed mmap R/W" }
    fn category(&self) -> Category     { Category::Memory }
    fn config(&self)   -> BenchmarkConfig {
        BenchmarkConfig { warmup_iters: 5, measure_iters: 50, unit: MetricUnit::GbPerSec }
    }

    fn run(&self, writer: Option<&RingBufferWriter>, cancel: &AtomicBool, bench_cfg: &BenchConfig) -> BenchResult {
        use std::ffi::CString;
        let cfg = self.config();
        let bench_id = 10u16;
        let size: usize = 32 * 1024 * 1024;  // 32 MB
        let mut samples = Vec::with_capacity(cfg.measure_iters as usize);

        let path = CString::new(format!("{}/.benchsuite_mmap_tmp", bench_cfg.storage_path)).unwrap();
        let fd = unsafe {
            libc::open(
                path.as_ptr(),
                O_RDWR | O_CREAT | O_TRUNC,
                0o600,
            )
        };
        if fd < 0 {
            // Storage permission unavailable (common in restricted contexts); return zero-result.
            return BenchResult::default();
        }
        unsafe { libc::ftruncate(fd, size as off_t) };

        for _ in 0..cfg.warmup_iters {
            unsafe {
                let p = libc::mmap(null_mut(), size, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
                libc::memset(p, 0xBB, size);
                libc::msync(p, size, MS_SYNC);
                libc::munmap(p, size);
            }
        }

        for i in 0..cfg.measure_iters {
            if i % 10 == 0 && cancel.load(Ordering::Acquire) { break; }
            let elapsed = time_ns!({
                unsafe {
                    let p = libc::mmap(null_mut(), size, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
                    libc::memset(p, 0xCC, size);
                    libc::msync(p, size, MS_SYNC);
                    libc::munmap(p, size);
                }
            });
            if elapsed == 0 { continue; }
            let gbps = size as f64 / elapsed as f64;
            samples.push(gbps);
            emit_sample(writer, bench_id, Category::Memory, 1, i, cfg.measure_iters, gbps, MetricUnit::GbPerSec, 0);
        }

        unsafe {
            libc::close(fd);
            libc::unlink(path.as_ptr());
        }

        BenchResult::from_samples(samples, MetricUnit::GbPerSec)
    }
}
