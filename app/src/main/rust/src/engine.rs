use std::sync::atomic::{AtomicBool, Ordering};
use std::collections::HashMap;
use std::env::consts::ARCH;
use std::time::{SystemTime, UNIX_EPOCH};
use libc::{CLOCK_MONOTONIC, _SC_NPROCESSORS_CONF, timespec};

use crate::benchmarks::{registry, BenchConfig, BenchResult, Category};
use crate::benchsuite::{
    BenchmarkResult, CategoryResult, DeviceSnapshot, RunMetadata, SuiteResults,
};
use crate::ring_buffer::{
    RingBufferWriter, STATE_DONE, STATE_ERROR, STATE_RUNNING, RECORD_COMPLETE,
    RECORD_PROGRESS,
};

/// Runs the benchmark suite sequentially.
/// Writes progress records before each benchmark and a COMPLETE record at the end.
pub fn run_suite(writer: Option<&RingBufferWriter>, cancel: &AtomicBool, storage_path: &str) {
    let all_benchmarks = registry();
    let total = all_benchmarks.len() as u32;
    let started_at_ms = wall_clock_ms();

    if let Some(w) = writer {
        w.set_state(STATE_RUNNING);
    }

    let mut results: Vec<(String, String, Category, BenchResult)> = Vec::new();

    for (progress_idx, bench) in all_benchmarks.iter().enumerate() {
        if cancel.load(Ordering::Acquire) {
            break;
        }

        if let Some(w) = writer {
            w.write_record(
                RECORD_PROGRESS,
                bench.category() as u8,
                progress_idx as u16,
                0, 0, 0,
                progress_idx as u32, total,
                monotonic_ns(), 0.0, 0.0, 0, 0,
            );
        }

        let cfg    = BenchConfig { storage_path: storage_path.to_owned() };
        let result = bench.run(writer, cancel, &cfg);
        results.push((bench.id().to_owned(), bench.name().to_owned(), bench.category(), result));
    }

    store_results(build_suite_results(results, started_at_ms, wall_clock_ms()));

    if let Some(w) = writer {
        w.write_record(
            RECORD_COMPLETE,
            0, 0, 0, 0, 0, 0, 0,
            monotonic_ns(), 0.0, 0.0, 0, 0,
        );
        w.set_state(STATE_DONE);
    }
}

/// Runs a single benchmark by registry index.
pub fn run_single_benchmark(
    bench_id: usize,
    writer: Option<&RingBufferWriter>,
    cancel: &AtomicBool,
    storage_path: &str,
) {
    let benchmarks = registry();
    let bench = match benchmarks.get(bench_id) {
        Some(b) => b,
        None    => {
            if let Some(w) = writer { w.set_state(STATE_ERROR); }
            return;
        }
    };

    if let Some(w) = writer {
        w.set_state(STATE_RUNNING);
        w.write_record(
            RECORD_PROGRESS,
            bench.category() as u8,
            bench_id as u16,
            0, 0, 0,
            0, 1,
            monotonic_ns(), 0.0, 0.0, 0, 0,
        );
    }

    let cfg = BenchConfig { storage_path: storage_path.to_owned() };
    let started_at_ms = wall_clock_ms();
    let result = bench.run(writer, cancel, &cfg);

    let results = vec![(bench.id().to_owned(), bench.name().to_owned(), bench.category(), result)];
    store_results(build_suite_results(results, started_at_ms, wall_clock_ms()));

    if let Some(w) = writer {
        w.write_record(RECORD_COMPLETE, 0, 0, 0, 0, 0, 0, 0, monotonic_ns(), 0.0, 0.0, 0, 0);
        w.set_state(STATE_DONE);
    }
}

/// Returns the most recently stored [SuiteResults].
/// If no run has completed yet, returns an empty result set.
pub fn collect_results(_writer: Option<&RingBufferWriter>) -> SuiteResults {
    LAST_RESULTS.lock().clone().unwrap_or_else(|| SuiteResults {
        categories: vec![],
        device:     Some(collect_device_snapshot()),
        metadata:   Some(RunMetadata {
            app_version:      env!("CARGO_PKG_VERSION").to_owned(),
            stability_rating: "".to_owned(),
            started_at_ms:    0,
            completed_at_ms:  0,
        }),
    })
}

// --- Result construction ---

fn build_suite_results(
    raw: Vec<(String, String, Category, BenchResult)>,
    started_at_ms: i64,
    completed_at_ms: i64,
) -> SuiteResults {
    let mut by_category: HashMap<String, Vec<BenchmarkResult>> = HashMap::new();

    for (bench_id, bench_name, category, result) in raw {
        let cat_str = category.to_string();
        let br = BenchmarkResult {
            id:           bench_id,
            name:         bench_name,
            metric_p50:   result.p50,
            metric_p99:   result.p99,
            metric_best:  result.best,
            metric_mean:  result.mean,
            throughput:   result.throughput,
            unit:         result.unit.to_string(),
            variance_pct: result.variance_pct,
        };
        by_category.entry(cat_str).or_default().push(br);
    }

    let categories: Vec<CategoryResult> = by_category
        .into_iter()
        .map(|(cat, benchmarks)| CategoryResult { category: cat, benchmarks })
        .collect();

    SuiteResults {
        categories,
        device:   Some(collect_device_snapshot()),
        metadata: Some(RunMetadata {
            app_version:      env!("CARGO_PKG_VERSION").to_owned(),
            stability_rating: "".to_owned(),
            started_at_ms,
            completed_at_ms,
        }),
    }
}

fn collect_device_snapshot() -> DeviceSnapshot {
    DeviceSnapshot {
        brand:         "Unknown".to_owned(),
        model:         "Unknown".to_owned(),
        soc:           "Unknown".to_owned(),
        abi:           ARCH.to_owned(),
        cpu_cores:     num_cpus(),
        ram_bytes:     0,
        android_api:   0,
    }
}

fn num_cpus() -> i32 {
    unsafe { libc::sysconf(_SC_NPROCESSORS_CONF) as i32 }
}

fn wall_clock_ms() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as i64
}

/// Monotonic timestamp in nanoseconds.
pub fn monotonic_ns() -> u64 {
    let mut ts = timespec { tv_sec: 0, tv_nsec: 0 };
    unsafe { libc::clock_gettime(CLOCK_MONOTONIC, &mut ts) };
    (ts.tv_sec as u64) * 1_000_000_000 + ts.tv_nsec as u64
}

// --- Per-process result cache ---
use parking_lot::Mutex;
use once_cell::sync::Lazy;

static LAST_RESULTS: Lazy<Mutex<Option<SuiteResults>>> = Lazy::new(|| Mutex::new(None));

fn store_results(results: SuiteResults) {
    *LAST_RESULTS.lock() = Some(results);
}
