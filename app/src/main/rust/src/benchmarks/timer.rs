use std::sync::atomic::{AtomicBool, Ordering};
use libc::{CLOCK_MONOTONIC, timespec};

use crate::benchmarks::{
    Benchmark, BenchConfig, BenchmarkConfig, BenchResult, Category, MetricUnit, emit_sample,
};
use crate::ring_buffer::RingBufferWriter;

macro_rules! time_ns {
    ($body:block) => {{
        let mut t0 = timespec { tv_sec: 0, tv_nsec: 0 };
        let mut t1 = timespec { tv_sec: 0, tv_nsec: 0 };
        unsafe { libc::clock_gettime(CLOCK_MONOTONIC, &mut t0) };
        let _ = $body;
        unsafe { libc::clock_gettime(CLOCK_MONOTONIC, &mut t1) };
        (t1.tv_sec - t0.tv_sec) as u64 * 1_000_000_000 + (t1.tv_nsec - t0.tv_nsec) as u64
    }};
}

// --- nanosleep jitter measurement ---

/// Measures p50/p99 jitter of nanosleep(1ms) requests.
/// Jitter = (actual wakeup latency − requested latency).
pub struct NanosleepJitter;

impl Benchmark for NanosleepJitter {
    fn id(&self)       -> &'static str { "timer.nanosleep_jitter" }
    fn name(&self)     -> &'static str { "nanosleep(1ms) jitter" }
    fn category(&self) -> Category     { Category::Timer }
    fn config(&self)   -> BenchmarkConfig {
        BenchmarkConfig { warmup_iters: 50, measure_iters: 1_000, unit: MetricUnit::UsPerOp }
    }

    fn run(&self, writer: Option<&RingBufferWriter>, cancel: &AtomicBool, _cfg: &BenchConfig) -> BenchResult {
        let cfg = self.config();
        let bench_id = 32u16;
        let requested_ns: u64      = 1_000_000;   // 1 ms
        let requested_req          = timespec { tv_sec: 0, tv_nsec: requested_ns as libc::c_long };
        let mut samples            = Vec::with_capacity(cfg.measure_iters as usize);

        for _ in 0..cfg.warmup_iters {
            let mut rem = timespec { tv_sec: 0, tv_nsec: 0 };
            unsafe { libc::nanosleep(&requested_req, &mut rem) };
        }

        for i in 0..cfg.measure_iters {
            if i % 100 == 0 && cancel.load(Ordering::Acquire) { break; }
            let mut rem = timespec { tv_sec: 0, tv_nsec: 0 };
            let elapsed = time_ns!({ unsafe { libc::nanosleep(&requested_req, &mut rem) } });
            // Jitter is the overshoot beyond the requested sleep
            let jitter_us = ((elapsed as i64 - requested_ns as i64).max(0)) as f64 / 1000.0;
            samples.push(jitter_us);
            emit_sample(writer, bench_id, Category::Timer, 1, i, cfg.measure_iters, jitter_us, MetricUnit::UsPerOp, 0);
        }

        BenchResult::from_samples(samples, MetricUnit::UsPerOp)
    }
}

// --- nanosleep accuracy (1us requests) ---

/// Measures actual sleep duration accuracy for very short (1µs) requests.
/// On Android, nanosleep below the scheduler tick may fall-back to spin.
pub struct NanosleepAccuracy;

impl Benchmark for NanosleepAccuracy {
    fn id(&self)       -> &'static str { "timer.nanosleep_accuracy_1us" }
    fn name(&self)     -> &'static str { "nanosleep(1µs) accuracy" }
    fn category(&self) -> Category     { Category::Timer }
    fn config(&self)   -> BenchmarkConfig {
        BenchmarkConfig { warmup_iters: 500, measure_iters: 5_000, unit: MetricUnit::UsPerOp }
    }

    fn run(&self, writer: Option<&RingBufferWriter>, cancel: &AtomicBool, _cfg: &BenchConfig) -> BenchResult {
        let cfg = self.config();
        let bench_id = 33u16;
        let requested_ns: u64    = 1_000;   // 1 µs
        let requested_req        = timespec { tv_sec: 0, tv_nsec: requested_ns as libc::c_long };
        let mut samples          = Vec::with_capacity(cfg.measure_iters as usize);

        for _ in 0..cfg.warmup_iters {
            let mut rem = timespec { tv_sec: 0, tv_nsec: 0 };
            unsafe { libc::nanosleep(&requested_req, &mut rem) };
        }

        for i in 0..cfg.measure_iters {
            if i % 500 == 0 && cancel.load(Ordering::Acquire) { break; }
            let mut rem = timespec { tv_sec: 0, tv_nsec: 0 };
            let elapsed = time_ns!({ unsafe { libc::nanosleep(&requested_req, &mut rem) } });
            let actual_us = elapsed as f64 / 1000.0;
            samples.push(actual_us);
            emit_sample(writer, bench_id, Category::Timer, 1, i, cfg.measure_iters, actual_us, MetricUnit::UsPerOp, 0);
        }

        BenchResult::from_samples(samples, MetricUnit::UsPerOp)
    }
}

// --- Clock resolution measurement ---

/// Measures the effective resolution of CLOCK_MONOTONIC by calling clock_gettime
/// in a tight loop until the value changes, then records the timestamp delta.
pub struct ClockResolution;

impl Benchmark for ClockResolution {
    fn id(&self)       -> &'static str { "timer.clock_resolution" }
    fn name(&self)     -> &'static str { "CLOCK_MONOTONIC resolution" }
    fn category(&self) -> Category     { Category::Timer }
    fn config(&self)   -> BenchmarkConfig {
        BenchmarkConfig { warmup_iters: 100, measure_iters: 2_000, unit: MetricUnit::NsPerOp }
    }

    fn run(&self, writer: Option<&RingBufferWriter>, cancel: &AtomicBool, _cfg: &BenchConfig) -> BenchResult {
        let cfg = self.config();
        let bench_id = 34u16;
        let mut samples = Vec::with_capacity(cfg.measure_iters as usize);

        for _ in 0..cfg.warmup_iters {
            let (_, _) = measure_tick();
        }

        for i in 0..cfg.measure_iters {
            if i % 200 == 0 && cancel.load(Ordering::Acquire) { break; }
            let (tick_ns, _) = measure_tick();
            samples.push(tick_ns as f64);
            emit_sample(writer, bench_id, Category::Timer, 1, i, cfg.measure_iters, tick_ns as f64, MetricUnit::NsPerOp, 0);
        }

        BenchResult::from_samples(samples, MetricUnit::NsPerOp)
    }
}

/// Polls clock_gettime until the timestamp changes, returning the delta.
fn measure_tick() -> (u64, u64) {
    let start = read_ns();
    let mut cur = start;
    while cur == start {
        cur = read_ns();
    }
    (cur - start, cur)
}

fn read_ns() -> u64 {
    let mut ts = timespec { tv_sec: 0, tv_nsec: 0 };
    unsafe { libc::clock_gettime(CLOCK_MONOTONIC, &mut ts) };
    (ts.tv_sec as u64) * 1_000_000_000 + ts.tv_nsec as u64
}
