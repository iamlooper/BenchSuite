use std::sync::atomic::AtomicBool;
use std::fmt;

use crate::ring_buffer::{RingBufferWriter, RECORD_SAMPLE};
use crate::engine::monotonic_ns;

pub mod cpu;
pub mod memory;
pub mod scheduler;
pub mod ipc;
pub mod io;
pub mod network;
pub mod timer;

// --- Category ---

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
#[repr(u8)]
pub enum Category {
    Cpu       = 0,
    Memory    = 1,
    Scheduler = 2,
    Ipc       = 3,
    Io        = 4,
    Network   = 5,
    Timer     = 6,
}

impl fmt::Display for Category {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        let s = match self {
            Category::Cpu       => "cpu",
            Category::Memory    => "memory",
            Category::Scheduler => "scheduler",
            Category::Ipc       => "ipc",
            Category::Io        => "io",
            Category::Network   => "network",
            Category::Timer     => "timer",
        };
        f.write_str(s)
    }
}

// --- MetricUnit ---

#[derive(Debug, Clone, Copy, Default)]
pub enum MetricUnit {
    #[default]
    NsPerOp,
    UsPerOp,
    MbPerSec,
    GbPerSec,
    OpsPerSec,
}

impl fmt::Display for MetricUnit {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        let s = match self {
            MetricUnit::NsPerOp   => "ns_per_op",
            MetricUnit::UsPerOp   => "us_per_op",
            MetricUnit::MbPerSec  => "mb_per_sec",
            MetricUnit::GbPerSec  => "gb_per_sec",
            MetricUnit::OpsPerSec => "ops_per_sec",
        };
        f.write_str(s)
    }
}

// --- BenchmarkConfig (static, per-benchmark) ---

pub struct BenchmarkConfig {
    pub warmup_iters:  u32,
    pub measure_iters: u32,
    pub unit:          MetricUnit,
}

/// Runtime configuration passed from Kotlin at run-start - fields that are not
/// known at compile time (e.g. the app's private storage directory).
pub struct BenchConfig {
    /// Absolute path to `Context.filesDir`. Benchmarks that need filesystem
    /// access write their scratch files here instead of world-accessible paths.
    pub storage_path: String,
}

// --- BenchResult ---

/// Aggregated statistics from a single benchmark run.
#[derive(Debug, Clone, Default)]
pub struct BenchResult {
    pub p50:          f64,
    pub p99:          f64,
    pub best:         f64,
    pub mean:         f64,
    pub throughput:   f64,
    pub variance_pct: f64,
    pub unit:         MetricUnit,
}

impl BenchResult {
    /// Compute statistics from a raw sample vector (values in native units).
    pub fn from_samples(mut samples: Vec<f64>, unit: MetricUnit) -> Self {
        if samples.is_empty() {
            return Self { unit, ..Default::default() };
        }
        samples.sort_by(f64::total_cmp);
        let n    = samples.len();
        let mean = samples.iter().sum::<f64>() / n as f64;
        let p50  = samples[n / 2];
        let p99  = samples[(n * 99) / 100];
        let best = samples[0];  // lowest = best for latency; highest for throughput

        // Use IQR-based dispersion instead of classic CV% (stddev/mean).
        // System benchmarks produce heavy-tailed distributions where rare outliers
        // (scheduler preemptions, cache misses) inflate stddev by 10-100×.
        // IQR considers only the middle 50% of samples, making it robust to outliers.
        let q1  = samples[n / 4];
        let q3  = samples[(n * 3) / 4];
        let iqr = q3 - q1;
        let variance_pct = if p50.abs() > 0.0 {
            ((iqr / p50) * 100.0).clamp(0.0, 999.0)
        } else {
            0.0
        };

        let throughput = match unit {
            MetricUnit::MbPerSec | MetricUnit::GbPerSec | MetricUnit::OpsPerSec => mean,
            _ => if mean > 0.0 { 1_000_000_000.0 / mean } else { 0.0 },
        };

        Self { p50, p99, best, mean, throughput, variance_pct, unit }
    }
}

// --- Benchmark trait ---

/// Every benchmark implements this trait. All benchmarks use only libc wrappers.
pub trait Benchmark: Send + Sync {
    /// Unique dot-separated identifier, e.g. `"cpu.clock_gettime_libc"`.
    fn id(&self) -> &'static str;

    /// Human-readable name.
    fn name(&self) -> &'static str;

    /// Category.
    fn category(&self) -> Category;

    /// Benchmark configuration (warm-up iters, measurement iters, unit).
    fn config(&self) -> BenchmarkConfig;

    /// Run the benchmark. Writes SAMPLE records to [writer] for each measurement.
    /// Checks [cancel] periodically. [cfg] carries runtime parameters from Kotlin.
    fn run(&self, writer: Option<&RingBufferWriter>, cancel: &AtomicBool, cfg: &BenchConfig) -> BenchResult;
}

// --- Registry ---

/// Returns the full ordered list of all registered benchmarks.
/// A benchmark's position in this slice is its numeric bench_id.
pub fn registry() -> Vec<Box<dyn Benchmark>> {
    vec![
        // CPU & Syscall (5)
        Box::new(cpu::ClockGettime),
        Box::new(cpu::GetPid),
        Box::new(cpu::SchedYield),
        Box::new(cpu::ThreadCreate),
        Box::new(cpu::ContextSwitch),
        // Memory (6)
        Box::new(memory::MmapAnonymousFault),
        Box::new(memory::MmapCycle),
        Box::new(memory::MemcpyBandwidth),
        Box::new(memory::StrideSweep),
        Box::new(memory::MallocFree),
        Box::new(memory::FileBackedMmap),
        // Scheduler (5)
        Box::new(scheduler::MutexPingPong),
        Box::new(scheduler::BarrierLatency),
        Box::new(scheduler::RwlockContention),
        Box::new(scheduler::YieldStorm),
        Box::new(scheduler::MessageFlood),
        // IPC (6)
        Box::new(ipc::PipeThroughput),
        Box::new(ipc::PipeLatency),
        Box::new(ipc::UnixSocketStream),
        Box::new(ipc::UnixSocketDgram),
        Box::new(ipc::EpollWakeup),
        Box::new(ipc::EpollScalability),
        // Storage I/O (6)
        Box::new(io::SeqWrite),
        Box::new(io::SeqRead),
        Box::new(io::RandWrite),
        Box::new(io::RandRead),
        Box::new(io::MmapFileRw),
        Box::new(io::MetadataOps),
        // Network (4)
        Box::new(network::TcpThroughput),
        Box::new(network::TcpLatency),
        Box::new(network::UdpPps),
        Box::new(network::EpollServer),
        // Timers (3)
        Box::new(timer::NanosleepJitter),
        Box::new(timer::NanosleepAccuracy),
        Box::new(timer::ClockResolution),
    ]
}

// --- Emit helper ---

/// Emits a SAMPLE record for one measured value.
#[allow(clippy::too_many_arguments)]
pub fn emit_sample(
    writer:    Option<&RingBufferWriter>,
    bench_id:  u16,
    cat:       Category,
    phase:     u32,
    iter:      u32,
    total:     u32,
    value:     f64,
    unit:      MetricUnit,
    metric_id: u32,
) {
    let unit_id: u32 = match unit {
        MetricUnit::NsPerOp   => 0,
        MetricUnit::MbPerSec  => 1,
        MetricUnit::OpsPerSec => 2,
        MetricUnit::UsPerOp   => 3,
        MetricUnit::GbPerSec  => 4,
    };
    if let Some(w) = writer {
        w.write_record(
            RECORD_SAMPLE,
            cat as u8,
            bench_id,
            0, 0, phase, iter, total,
            monotonic_ns(),
            value, 0.0,
            unit_id,
            metric_id,
        );
    }
}
