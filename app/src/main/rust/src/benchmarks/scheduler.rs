use std::sync::atomic::{AtomicBool, AtomicU32, AtomicU64, Ordering};
use std::sync::{Arc, Barrier, RwLock};
use libc::{CLOCK_MONOTONIC, PTHREAD_MUTEX_INITIALIZER, pthread_cond_t, pthread_mutex_t, timespec};

use crate::benchmarks::{
    Benchmark, BenchConfig, BenchmarkConfig, BenchResult, Category, MetricUnit, emit_sample,
};
use crate::ring_buffer::RingBufferWriter;
use std::mem::zeroed;
use std::ptr::null;
use std::thread;
use std::time::Duration;

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

// --- Mutex ping-pong ---

pub struct MutexPingPong;

impl Benchmark for MutexPingPong {
    fn id(&self)       -> &'static str { "scheduler.mutex_pingpong" }
    fn name(&self)     -> &'static str { "Mutex ping-pong (2 threads)" }
    fn category(&self) -> Category     { Category::Scheduler }
    fn config(&self)   -> BenchmarkConfig {
        BenchmarkConfig { warmup_iters: 200, measure_iters: 5_000, unit: MetricUnit::NsPerOp }
    }

    fn run(&self, writer: Option<&RingBufferWriter>, cancel: &AtomicBool, _cfg: &BenchConfig) -> BenchResult {
        let cfg = self.config();
        let bench_id = 11u16;
        let total = cfg.warmup_iters + cfg.measure_iters;
        let mut samples = Vec::with_capacity(cfg.measure_iters as usize);

        let mutex: Arc<pthread_mutex_t> = Arc::new(PTHREAD_MUTEX_INITIALIZER);
        let cond_a: Arc<pthread_cond_t> = Arc::new(unsafe { zeroed() });
        let cond_b: Arc<pthread_cond_t> = Arc::new(unsafe { zeroed() });

        unsafe {
            libc::pthread_mutex_init(
                Arc::as_ptr(&mutex) as *mut _,
                null(),
            );
            libc::pthread_cond_init(Arc::as_ptr(&cond_a) as *mut _, null());
            libc::pthread_cond_init(Arc::as_ptr(&cond_b) as *mut _, null());
        }

        let _counter = Arc::new(AtomicU64::new(0));
        let turn    = Arc::new(AtomicU32::new(0)); // 0 = main, 1 = peer

        let mutex2   = mutex.clone();
        let cond_a2  = cond_a.clone();
        let cond_b2  = cond_b.clone();
        let turn2    = turn.clone();
        let total_u  = total as u64;

        let handle = thread::spawn(move || {
            let m = Arc::as_ptr(&mutex2) as *mut pthread_mutex_t;
            let ca = Arc::as_ptr(&cond_a2) as *mut pthread_cond_t;
            let cb = Arc::as_ptr(&cond_b2) as *mut pthread_cond_t;
            let mut i = 0u64;
            loop {
                unsafe { libc::pthread_mutex_lock(m) };
                while turn2.load(Ordering::Acquire) != 1 && i < total_u {
                    unsafe { libc::pthread_cond_wait(ca, m) };
                }
                if i >= total_u {
                    unsafe { libc::pthread_mutex_unlock(m) };
                    break;
                }
                turn2.store(0, Ordering::Release);
                unsafe {
                    libc::pthread_cond_signal(cb);
                    libc::pthread_mutex_unlock(m);
                }
                i += 1;
            }
        });

        let m  = Arc::as_ptr(&mutex) as *mut pthread_mutex_t;
        let ca = Arc::as_ptr(&cond_a) as *mut pthread_cond_t;
        let cb = Arc::as_ptr(&cond_b) as *mut pthread_cond_t;

        for i in 0..total {
            let measuring = i >= cfg.warmup_iters;
            let start_ns = if measuring { unsafe { let mut t = timespec { tv_sec: 0, tv_nsec: 0 }; libc::clock_gettime(CLOCK_MONOTONIC, &mut t); (t.tv_sec as u64)*1_000_000_000 + t.tv_nsec as u64 } } else { 0 };

            unsafe {
                libc::pthread_mutex_lock(m);
                turn.store(1, Ordering::Release);
                libc::pthread_cond_signal(ca);
                while turn.load(Ordering::Acquire) != 0 {
                    libc::pthread_cond_wait(cb, m);
                }
                libc::pthread_mutex_unlock(m);
            }

            if measuring {
                let mut t1 = timespec { tv_sec: 0, tv_nsec: 0 };
                unsafe { libc::clock_gettime(CLOCK_MONOTONIC, &mut t1) };
                let end_ns = (t1.tv_sec as u64)*1_000_000_000 + t1.tv_nsec as u64;
                let ns = (end_ns - start_ns) as f64 / 2.0; // half round-trip = one-way
                let idx = i - cfg.warmup_iters;
                samples.push(ns);
                emit_sample(writer, bench_id, Category::Scheduler, 1, idx, cfg.measure_iters, ns, MetricUnit::NsPerOp, 0);
            }

            if cancel.load(Ordering::Acquire) { break; }
        }

        // Signal peer to terminate (by pushing turn count past total)
        unsafe {
            libc::pthread_mutex_lock(m);
            turn.store(1, Ordering::Release);
            libc::pthread_cond_signal(ca);
            libc::pthread_mutex_unlock(m);
        }
        handle.join().ok();
        unsafe {
            libc::pthread_mutex_destroy(m);
            libc::pthread_cond_destroy(ca);
            libc::pthread_cond_destroy(cb);
        }

        BenchResult::from_samples(samples, MetricUnit::NsPerOp)
    }
}

// --- Barrier latency ---

pub struct BarrierLatency;

impl Benchmark for BarrierLatency {
    fn id(&self)       -> &'static str { "scheduler.barrier_latency" }
    fn name(&self)     -> &'static str { "pthread_barrier latency (4 threads)" }
    fn category(&self) -> Category     { Category::Scheduler }
    fn config(&self)   -> BenchmarkConfig {
        BenchmarkConfig { warmup_iters: 100, measure_iters: 2_000, unit: MetricUnit::UsPerOp }
    }

    fn run(&self, writer: Option<&RingBufferWriter>, cancel: &AtomicBool, _cfg: &BenchConfig) -> BenchResult {
        let cfg = self.config();
        let bench_id = 12u16;
        let n_threads = 4usize;
        let total = cfg.warmup_iters + cfg.measure_iters;
        let mut samples = Vec::with_capacity(cfg.measure_iters as usize);

        let barrier = Arc::new(Barrier::new(n_threads));

        let mut handles = Vec::new();
        for _ in 0..(n_threads - 1) {
            let b = barrier.clone();
            let t = total as usize;
            handles.push(thread::spawn(move || {
                for _ in 0..t { b.wait(); }
            }));
        }

        for i in 0..total {
            let measuring = i >= cfg.warmup_iters;
            let start_ns = unsafe {
                let mut t = timespec { tv_sec: 0, tv_nsec: 0 };
                libc::clock_gettime(CLOCK_MONOTONIC, &mut t);
                (t.tv_sec as u64) * 1_000_000_000 + t.tv_nsec as u64
            };
            barrier.wait();
            if measuring {
                let mut t1 = timespec { tv_sec: 0, tv_nsec: 0 };
                unsafe { libc::clock_gettime(CLOCK_MONOTONIC, &mut t1) };
                let end_ns = (t1.tv_sec as u64) * 1_000_000_000 + t1.tv_nsec as u64;
                let us = (end_ns - start_ns) as f64 / 1000.0;
                let idx = i - cfg.warmup_iters;
                samples.push(us);
                emit_sample(writer, bench_id, Category::Scheduler, 1, idx, cfg.measure_iters, us, MetricUnit::UsPerOp, 0);
            }
            if cancel.load(Ordering::Acquire) { break; }
        }

        for h in handles { h.join().ok(); }
        BenchResult::from_samples(samples, MetricUnit::UsPerOp)
    }
}

// --- RwLock contention ---

pub struct RwlockContention;

impl Benchmark for RwlockContention {
    fn id(&self)       -> &'static str { "scheduler.rwlock_contention" }
    fn name(&self)     -> &'static str { "pthread_rwlock read-contention (8 readers)" }
    fn category(&self) -> Category     { Category::Scheduler }
    fn config(&self)   -> BenchmarkConfig {
        BenchmarkConfig { warmup_iters: 200, measure_iters: 5_000, unit: MetricUnit::NsPerOp }
    }

    fn run(&self, writer: Option<&RingBufferWriter>, cancel: &AtomicBool, _cfg: &BenchConfig) -> BenchResult {
        let cfg = self.config();
        let bench_id = 13u16;
        let mut samples = Vec::with_capacity(cfg.measure_iters as usize);

        let rwlock: Arc<RwLock<u64>> = Arc::new(RwLock::new(0u64));
        let n_readers = 7usize;
        let done = Arc::new(AtomicBool::new(false));
        let mut handles = Vec::new();

        for _ in 0..n_readers {
            let rw = rwlock.clone();
            let d  = done.clone();
            handles.push(thread::spawn(move || {
                while !d.load(Ordering::Relaxed) {
                    let _ = rw.read().map(|g| *g);
                }
            }));
        }

        for _ in 0..cfg.warmup_iters {
            let _ = rwlock.read().map(|g| *g);
        }

        for i in 0..cfg.measure_iters {
            if i % 500 == 0 && cancel.load(Ordering::Acquire) { break; }
            let elapsed = time_ns!({ let _ = rwlock.read().map(|g| *g); });
            let ns = elapsed as f64;
            samples.push(ns);
            emit_sample(writer, bench_id, Category::Scheduler, 1, i, cfg.measure_iters, ns, MetricUnit::NsPerOp, 0);
        }

        done.store(true, Ordering::Release);
        for h in handles { h.join().ok(); }
        BenchResult::from_samples(samples, MetricUnit::NsPerOp)
    }
}

// --- Yield storm ---

pub struct YieldStorm;

impl Benchmark for YieldStorm {
    fn id(&self)       -> &'static str { "scheduler.yield_storm" }
    fn name(&self)     -> &'static str { "sched_yield storm (8 threads)" }
    fn category(&self) -> Category     { Category::Scheduler }
    fn config(&self)   -> BenchmarkConfig {
        BenchmarkConfig { warmup_iters: 0, measure_iters: 20, unit: MetricUnit::OpsPerSec }
    }

    fn run(&self, writer: Option<&RingBufferWriter>, cancel: &AtomicBool, _cfg: &BenchConfig) -> BenchResult {
        let cfg = self.config();
        let bench_id = 14u16;
        let duration_ms = 200u64;
        let n_threads   = 8usize;
        let mut samples = Vec::with_capacity(cfg.measure_iters as usize);

        for i in 0..cfg.measure_iters {
            if cancel.load(Ordering::Acquire) { break; }
            let counts: Vec<Arc<AtomicU64>> = (0..n_threads)
                .map(|_| Arc::new(AtomicU64::new(0)))
                .collect();
            let stop = Arc::new(AtomicBool::new(false));
            let mut handles = Vec::new();

            for c in &counts {
                let c2   = c.clone();
                let stop = stop.clone();
                handles.push(thread::spawn(move || {
                    while !stop.load(Ordering::Relaxed) {
                        unsafe { libc::sched_yield() };
                        c2.fetch_add(1, Ordering::Relaxed);
                    }
                }));
            }

            let start_ns = unsafe {
                let mut t = timespec { tv_sec: 0, tv_nsec: 0 };
                libc::clock_gettime(CLOCK_MONOTONIC, &mut t);
                (t.tv_sec as u64) * 1_000_000_000 + t.tv_nsec as u64
            };
            thread::sleep(Duration::from_millis(duration_ms));
            stop.store(true, Ordering::Release);
            let mut t1 = timespec { tv_sec: 0, tv_nsec: 0 };
            unsafe { libc::clock_gettime(CLOCK_MONOTONIC, &mut t1) };
            let elapsed_ns = (t1.tv_sec as u64) * 1_000_000_000 + t1.tv_nsec as u64 - start_ns;

            for h in handles { h.join().ok(); }
            let total_yields: u64 = counts.iter().map(|c| c.load(Ordering::Relaxed)).sum();
            if elapsed_ns == 0 { continue; }
            let ops_per_sec = total_yields as f64 / (elapsed_ns as f64 / 1e9);
            samples.push(ops_per_sec);
            emit_sample(writer, bench_id, Category::Scheduler, 1, i, cfg.measure_iters, ops_per_sec, MetricUnit::OpsPerSec, 0);
        }

        BenchResult::from_samples(samples, MetricUnit::OpsPerSec)
    }
}

// --- Message flood (MPSC via channel) ---

pub struct MessageFlood;

impl Benchmark for MessageFlood {
    fn id(&self)       -> &'static str { "scheduler.message_flood" }
    fn name(&self)     -> &'static str { "MPSC message flood (4 senders)" }
    fn category(&self) -> Category     { Category::Scheduler }
    fn config(&self)   -> BenchmarkConfig {
        BenchmarkConfig { warmup_iters: 5, measure_iters: 50, unit: MetricUnit::OpsPerSec }
    }

    fn run(&self, writer: Option<&RingBufferWriter>, cancel: &AtomicBool, _cfg: &BenchConfig) -> BenchResult {
        use std::sync::mpsc;
        let cfg = self.config();
        let bench_id = 15u16;
        let n_senders    = 4usize;
        let msgs_per_sender: u64 = 50_000;
        let mut samples = Vec::with_capacity(cfg.measure_iters as usize);

        for i in 0..cfg.measure_iters {
            if cancel.load(Ordering::Acquire) { break; }
            let (tx, rx) = mpsc::channel();
            let elapsed = time_ns!({
                let mut handles = Vec::new();
                for _ in 0..n_senders {
                    let tx2 = tx.clone();
                    handles.push(thread::spawn(move || {
                        for v in 0..msgs_per_sender { tx2.send(v).ok(); }
                    }));
                }
                drop(tx);
                while rx.recv().is_ok() {}
            });
            if elapsed == 0 { continue; }
            let ops = (n_senders as f64 * msgs_per_sender as f64) / (elapsed as f64 / 1e9);
            samples.push(ops);
            emit_sample(writer, bench_id, Category::Scheduler, 1, i, cfg.measure_iters, ops, MetricUnit::OpsPerSec, 0);
        }

        BenchResult::from_samples(samples, MetricUnit::OpsPerSec)
    }
}
