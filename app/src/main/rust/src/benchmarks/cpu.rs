use std::sync::atomic::{AtomicBool, Ordering};
use libc::{c_void, CLOCK_MONOTONIC, pthread_t, timespec};

use crate::benchmarks::{
    Benchmark, BenchConfig, BenchmarkConfig, BenchResult, Category, MetricUnit, emit_sample,
};
use crate::ring_buffer::RingBufferWriter;
use std::ptr::{null, null_mut};
use std::thread;

// --- Helper: raw clock_gettime timing ---

/// Returns elapsed nanoseconds for a block using clock_gettime(CLOCK_MONOTONIC).
macro_rules! time_ns {
    ($body:block) => {{
        let mut t0 = timespec { tv_sec: 0, tv_nsec: 0 };
        let mut t1 = timespec { tv_sec: 0, tv_nsec: 0 };
        unsafe { libc::clock_gettime(CLOCK_MONOTONIC, &mut t0) };
        let _ = $body;
        unsafe { libc::clock_gettime(CLOCK_MONOTONIC, &mut t1) };
        let elapsed = (t1.tv_sec - t0.tv_sec) as u64 * 1_000_000_000
            + (t1.tv_nsec - t0.tv_nsec) as u64;
        elapsed
    }};
}

// --- clock_gettime benchmark ---

pub struct ClockGettime;

impl Benchmark for ClockGettime {
    fn id(&self)       -> &'static str { "cpu.clock_gettime_libc" }
    fn name(&self)     -> &'static str { "clock_gettime (CLOCK_MONOTONIC)" }
    fn category(&self) -> Category     { Category::Cpu }
    fn config(&self)   -> BenchmarkConfig {
        BenchmarkConfig { warmup_iters: 2000, measure_iters: 10_000, unit: MetricUnit::NsPerOp }
    }

    fn run(&self, writer: Option<&RingBufferWriter>, cancel: &AtomicBool, _cfg: &BenchConfig) -> BenchResult {
        let cfg   = self.config();
        let bench_id = 0u16;
        let mut samples = Vec::with_capacity(cfg.measure_iters as usize);
        let mut ts = timespec { tv_sec: 0, tv_nsec: 0 };

        // Warmup
        for _ in 0..cfg.warmup_iters {
            unsafe { libc::clock_gettime(CLOCK_MONOTONIC, &mut ts) };
        }

        // Measure
        for i in 0..cfg.measure_iters {
            if i % 1000 == 0 && cancel.load(Ordering::Acquire) { break; }
            let elapsed = time_ns!({ unsafe { libc::clock_gettime(CLOCK_MONOTONIC, &mut ts) } });
            let ns = elapsed as f64;
            samples.push(ns);
            emit_sample(writer, bench_id, Category::Cpu, 1, i, cfg.measure_iters, ns, MetricUnit::NsPerOp, 0);
        }

        BenchResult::from_samples(samples, MetricUnit::NsPerOp)
    }
}

// --- getpid benchmark ---

pub struct GetPid;

impl Benchmark for GetPid {
    fn id(&self)       -> &'static str { "cpu.getpid_libc" }
    fn name(&self)     -> &'static str { "getpid()" }
    fn category(&self) -> Category     { Category::Cpu }
    fn config(&self)   -> BenchmarkConfig {
        BenchmarkConfig { warmup_iters: 5000, measure_iters: 20_000, unit: MetricUnit::NsPerOp }
    }

    fn run(&self, writer: Option<&RingBufferWriter>, cancel: &AtomicBool, _cfg: &BenchConfig) -> BenchResult {
        let cfg = self.config();
        let bench_id = 1u16;
        let mut samples = Vec::with_capacity(cfg.measure_iters as usize);

        // Warmup (fills vDSO cache)
        for _ in 0..cfg.warmup_iters { unsafe { libc::getpid() }; }

        // Measure - batch 10 calls per timing to reduce clock_gettime overhead ratio
        let batch = 10u32;
        for i in 0..(cfg.measure_iters / batch) {
            if i % 1000 == 0 && cancel.load(Ordering::Acquire) { break; }
            let elapsed = time_ns!({
                for _ in 0..batch { unsafe { libc::getpid() }; }
            });
            let ns_per_call = elapsed as f64 / batch as f64;
            samples.push(ns_per_call);
            emit_sample(writer, bench_id, Category::Cpu, 1, i, cfg.measure_iters / batch, ns_per_call, MetricUnit::NsPerOp, 0);
        }

        BenchResult::from_samples(samples, MetricUnit::NsPerOp)
    }
}

// --- sched_yield benchmark ---

pub struct SchedYield;

impl Benchmark for SchedYield {
    fn id(&self)       -> &'static str { "cpu.sched_yield_libc" }
    fn name(&self)     -> &'static str { "sched_yield()" }
    fn category(&self) -> Category     { Category::Cpu }
    fn config(&self)   -> BenchmarkConfig {
        BenchmarkConfig { warmup_iters: 500, measure_iters: 5_000, unit: MetricUnit::NsPerOp }
    }

    fn run(&self, writer: Option<&RingBufferWriter>, cancel: &AtomicBool, _cfg: &BenchConfig) -> BenchResult {
        let cfg = self.config();
        let bench_id = 2u16;
        let mut samples = Vec::with_capacity(cfg.measure_iters as usize);
        for _ in 0..cfg.warmup_iters { unsafe { libc::sched_yield() }; }

        for i in 0..cfg.measure_iters {
            if i % 500 == 0 && cancel.load(Ordering::Acquire) { break; }
            let elapsed = time_ns!({ unsafe { libc::sched_yield() } });
            let ns = elapsed as f64;
            samples.push(ns);
            emit_sample(writer, bench_id, Category::Cpu, 1, i, cfg.measure_iters, ns, MetricUnit::NsPerOp, 0);
        }

        BenchResult::from_samples(samples, MetricUnit::NsPerOp)
    }
}

// --- pthread_create / join benchmark ---

pub struct ThreadCreate;

impl Benchmark for ThreadCreate {
    fn id(&self)       -> &'static str { "cpu.thread_create_libc" }
    fn name(&self)     -> &'static str { "pthread_create + join" }
    fn category(&self) -> Category     { Category::Cpu }
    fn config(&self)   -> BenchmarkConfig {
        BenchmarkConfig { warmup_iters: 20, measure_iters: 500, unit: MetricUnit::UsPerOp }
    }

    fn run(&self, writer: Option<&RingBufferWriter>, cancel: &AtomicBool, _cfg: &BenchConfig) -> BenchResult {
        let cfg = self.config();
        let bench_id = 3u16;
        let mut samples = Vec::with_capacity(cfg.measure_iters as usize);

        extern "C" fn noop(_: *mut c_void) -> *mut c_void { null_mut() }

        // Warmup
        for _ in 0..cfg.warmup_iters {
            let mut tid: pthread_t = 0;
            unsafe {
                libc::pthread_create(&mut tid, null(), noop, null_mut());
                libc::pthread_join(tid, null_mut());
            }
        }

        for i in 0..cfg.measure_iters {
            if i % 50 == 0 && cancel.load(Ordering::Acquire) { break; }
            let elapsed = time_ns!({
                let mut tid: pthread_t = 0;
                unsafe {
                    libc::pthread_create(&mut tid, null(), noop, null_mut());
                    libc::pthread_join(tid, null_mut());
                }
            });
            let us = elapsed as f64 / 1000.0;
            samples.push(us);
            emit_sample(writer, bench_id, Category::Cpu, 1, i, cfg.measure_iters, us, MetricUnit::UsPerOp, 0);
        }

        BenchResult::from_samples(samples, MetricUnit::UsPerOp)
    }
}

// --- Context switch benchmark ---
// Self-pipe ping-pong between two threads: measures voluntary context switch latency.

pub struct ContextSwitch;

impl Benchmark for ContextSwitch {
    fn id(&self)       -> &'static str { "cpu.context_switch_libc" }
    fn name(&self)     -> &'static str { "Context switch round-trip (pipe)" }
    fn category(&self) -> Category     { Category::Cpu }
    fn config(&self)   -> BenchmarkConfig {
        BenchmarkConfig { warmup_iters: 100, measure_iters: 2_000, unit: MetricUnit::UsPerOp }
    }

    fn run(&self, writer: Option<&RingBufferWriter>, cancel: &AtomicBool, _cfg: &BenchConfig) -> BenchResult {
        let cfg = self.config();
        let bench_id = 4u16;
        let mut samples = Vec::with_capacity(cfg.measure_iters as usize);

        // Create two pipes for bidirectional ping-pong
        let mut pipe_a = [0i32; 2];
        let mut pipe_b = [0i32; 2];
        unsafe {
            libc::pipe(pipe_a.as_mut_ptr());
            libc::pipe(pipe_b.as_mut_ptr());
        }

        // Spawn a peer thread that echoes back
        let pipe_a_copy = pipe_a;
        let pipe_b_copy = pipe_b;
        let total_rounds = cfg.warmup_iters + cfg.measure_iters;
        let handle = thread::spawn(move || {
            let buf = [0u8; 1];
            for _ in 0..total_rounds {
                let mut recv = [0u8; 1];
                unsafe {
                    libc::read(pipe_a_copy[0], recv.as_mut_ptr() as _, 1);
                    libc::write(pipe_b_copy[1], buf.as_ptr() as _, 1);
                }
            }
        });

        let buf = [0u8; 1];
        // Warmup
        for _ in 0..cfg.warmup_iters {
            unsafe {
                libc::write(pipe_a[1], buf.as_ptr() as _, 1);
                let mut recv = [0u8; 1];
                libc::read(pipe_b[0], recv.as_mut_ptr() as _, 1);
            }
        }

        // Measure
        for i in 0..cfg.measure_iters {
            if i % 200 == 0 && cancel.load(Ordering::Acquire) { break; }
            let elapsed = time_ns!({
                unsafe {
                    libc::write(pipe_a[1], buf.as_ptr() as _, 1);
                    let mut recv = [0u8; 1];
                    libc::read(pipe_b[0], recv.as_mut_ptr() as _, 1);
                }
            });
            let us = elapsed as f64 / 1000.0;
            samples.push(us);
            emit_sample(writer, bench_id, Category::Cpu, 1, i, cfg.measure_iters, us, MetricUnit::UsPerOp, 0);
        }

        handle.join().ok();
        unsafe {
            for &fd in &[pipe_a[0], pipe_a[1], pipe_b[0], pipe_b[1]] {
                libc::close(fd);
            }
        }

        BenchResult::from_samples(samples, MetricUnit::UsPerOp)
    }
}
