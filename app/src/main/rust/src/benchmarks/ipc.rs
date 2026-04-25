use std::sync::atomic::{AtomicBool, Ordering};
use libc::{
    AF_UNIX, CLOCK_MONOTONIC, EPOLLET, EPOLLIN, EPOLL_CTL_ADD, SOCK_DGRAM, SOCK_STREAM,
    epoll_event, timespec,
};

use crate::benchmarks::{
    Benchmark, BenchConfig, BenchmarkConfig, BenchResult, Category, MetricUnit, emit_sample,
};
use crate::ring_buffer::RingBufferWriter;
use std::thread;

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

// --- Pipe throughput ---

pub struct PipeThroughput;

impl Benchmark for PipeThroughput {
    fn id(&self)       -> &'static str { "ipc.pipe_throughput" }
    fn name(&self)     -> &'static str { "Pipe throughput" }
    fn category(&self) -> Category     { Category::Ipc }
    fn config(&self)   -> BenchmarkConfig {
        BenchmarkConfig { warmup_iters: 5, measure_iters: 50, unit: MetricUnit::MbPerSec }
    }

    fn run(&self, writer: Option<&RingBufferWriter>, cancel: &AtomicBool, _cfg: &BenchConfig) -> BenchResult {
        let cfg = self.config();
        let bench_id = 16u16;
        let chunk: usize = 65536;
        let total_bytes: usize = 128 * 1024 * 1024;
        let mut samples = Vec::with_capacity(cfg.measure_iters as usize);

        let mut pipe_fds = [0i32; 2];
        unsafe { libc::pipe(pipe_fds.as_mut_ptr()) };

        let r = pipe_fds[0];
        let w = pipe_fds[1];

        for i in 0..cfg.measure_iters {
            if i % 5 == 0 && cancel.load(Ordering::Acquire) { break; }

            let total_u = total_bytes as u64;
            let handle = thread::spawn(move || {
                let buf = vec![0u8; chunk];
                let mut written = 0u64;
                while written < total_u {
                    let n = unsafe { libc::write(w, buf.as_ptr() as _, chunk) };
                    if n > 0 { written += n as u64; }
                }
            });

            let elapsed = time_ns!({
                let mut buf = vec![0u8; chunk];
                let mut read = 0usize;
                while read < total_bytes {
                    let n = unsafe { libc::read(r, buf.as_mut_ptr() as _, chunk) };
                    if n > 0 { read += n as usize; }
                }
            });

            handle.join().ok();
            if elapsed == 0 { continue; }
            let mbps = total_bytes as f64 / (elapsed as f64 / 1e9) / (1024.0 * 1024.0);
            samples.push(mbps);
            emit_sample(writer, bench_id, Category::Ipc, 1, i, cfg.measure_iters, mbps, MetricUnit::MbPerSec, 0);
        }

        unsafe {
            libc::close(pipe_fds[0]);
            libc::close(pipe_fds[1]);
        }

        BenchResult::from_samples(samples, MetricUnit::MbPerSec)
    }
}

// --- Pipe latency (single byte ping-pong) ---

pub struct PipeLatency;

impl Benchmark for PipeLatency {
    fn id(&self)       -> &'static str { "ipc.pipe_latency" }
    fn name(&self)     -> &'static str { "Pipe round-trip latency" }
    fn category(&self) -> Category     { Category::Ipc }
    fn config(&self)   -> BenchmarkConfig {
        BenchmarkConfig { warmup_iters: 100, measure_iters: 3_000, unit: MetricUnit::UsPerOp }
    }

    fn run(&self, writer: Option<&RingBufferWriter>, cancel: &AtomicBool, _cfg: &BenchConfig) -> BenchResult {
        let cfg = self.config();
        let bench_id = 17u16;
        let mut samples = Vec::with_capacity(cfg.measure_iters as usize);
        let mut pa = [0i32; 2];
        let mut pb = [0i32; 2];
        unsafe { libc::pipe(pa.as_mut_ptr()); libc::pipe(pb.as_mut_ptr()); }

        let total = cfg.warmup_iters + cfg.measure_iters;
        let handle = thread::spawn(move || {
            for _ in 0..total {
                let mut b = [0u8; 1];
                unsafe { libc::read(pa[0], b.as_mut_ptr() as _, 1); libc::write(pb[1], b.as_ptr() as _, 1); }
            }
        });

        let buf = [0xFFu8; 1];
        for i in 0..total {
            let measuring = i >= cfg.warmup_iters;
            if measuring && i % 300 == 0 && cancel.load(Ordering::Acquire) { break; }
            let elapsed = time_ns!({
                unsafe {
                    libc::write(pa[1], buf.as_ptr() as _, 1);
                    let mut b = [0u8; 1];
                    libc::read(pb[0], b.as_mut_ptr() as _, 1);
                }
            });
            if measuring {
                let us = elapsed as f64 / 1000.0;
                let idx = i - cfg.warmup_iters;
                samples.push(us);
                emit_sample(writer, bench_id, Category::Ipc, 1, idx, cfg.measure_iters, us, MetricUnit::UsPerOp, 0);
            }
        }

        handle.join().ok();
        unsafe {
            for fd in [pa[0], pa[1], pb[0], pb[1]] { libc::close(fd); }
        }

        BenchResult::from_samples(samples, MetricUnit::UsPerOp)
    }
}

// --- Unix domain socket stream ---

pub struct UnixSocketStream;

impl Benchmark for UnixSocketStream {
    fn id(&self)       -> &'static str { "ipc.unix_socket_stream" }
    fn name(&self)     -> &'static str { "Unix socket stream throughput" }
    fn category(&self) -> Category     { Category::Ipc }
    fn config(&self)   -> BenchmarkConfig {
        BenchmarkConfig { warmup_iters: 5, measure_iters: 30, unit: MetricUnit::MbPerSec }
    }

    fn run(&self, writer: Option<&RingBufferWriter>, cancel: &AtomicBool, _cfg: &BenchConfig) -> BenchResult {
        let cfg = self.config();
        let bench_id = 18u16;
        let mut fds = [0i32; 2];
        // SOCK_STREAM + SOCK_DGRAM via socketpair - avoids filesystem path
        if unsafe { libc::socketpair(AF_UNIX, SOCK_STREAM, 0, fds.as_mut_ptr()) } < 0 {
            return BenchResult::default();
        }
        let total_bytes: usize = 64 * 1024 * 1024;
        let chunk: usize = 65536;
        let mut samples = Vec::with_capacity(cfg.measure_iters as usize);

        for i in 0..cfg.measure_iters {
            if cancel.load(Ordering::Acquire) { break; }
            let fd_w = fds[1];
            let total_u = total_bytes as u64;
            let handle = thread::spawn(move || {
                let buf = vec![0u8; chunk];
                let mut written = 0u64;
                while written < total_u {
                    let n = unsafe { libc::send(fd_w, buf.as_ptr() as _, chunk, 0) };
                    if n > 0 { written += n as u64; }
                }
            });

            let elapsed = time_ns!({
                let mut buf = vec![0u8; chunk];
                let mut read = 0usize;
                while read < total_bytes {
                    let n = unsafe { libc::recv(fds[0], buf.as_mut_ptr() as _, chunk, 0) };
                    if n > 0 { read += n as usize; }
                }
            });

            handle.join().ok();
            if elapsed == 0 { continue; }
            let mbps = total_bytes as f64 / (elapsed as f64 / 1e9) / (1024.0 * 1024.0);
            samples.push(mbps);
            emit_sample(writer, bench_id, Category::Ipc, 1, i, cfg.measure_iters, mbps, MetricUnit::MbPerSec, 0);
        }

        unsafe { libc::close(fds[0]); libc::close(fds[1]); }
        BenchResult::from_samples(samples, MetricUnit::MbPerSec)
    }
}

// --- Unix domain socket datagram ---

pub struct UnixSocketDgram;

impl Benchmark for UnixSocketDgram {
    fn id(&self)       -> &'static str { "ipc.unix_socket_dgram" }
    fn name(&self)     -> &'static str { "Unix socket datagram PPS" }
    fn category(&self) -> Category     { Category::Ipc }
    fn config(&self)   -> BenchmarkConfig {
        BenchmarkConfig { warmup_iters: 5, measure_iters: 30, unit: MetricUnit::OpsPerSec }
    }

    fn run(&self, writer: Option<&RingBufferWriter>, cancel: &AtomicBool, _cfg: &BenchConfig) -> BenchResult {
        let cfg = self.config();
        let bench_id = 19u16;
        let mut fds = [0i32; 2];
        if unsafe { libc::socketpair(AF_UNIX, SOCK_DGRAM, 0, fds.as_mut_ptr()) } < 0 {
            return BenchResult::default();
        }
        let n_datagrams: u64 = 50_000;
        let mut samples = Vec::with_capacity(cfg.measure_iters as usize);

        for i in 0..cfg.measure_iters {
            if cancel.load(Ordering::Acquire) { break; }
            let fd_w = fds[1];
            let n = n_datagrams;
            let handle = thread::spawn(move || {
                let buf = [0x42u8; 64];
                for _ in 0..n { unsafe { libc::send(fd_w, buf.as_ptr() as _, 64, 0) }; }
            });

            let elapsed = time_ns!({
                let mut buf = [0u8; 64];
                let mut received = 0u64;
                while received < n_datagrams {
                    let r = unsafe { libc::recv(fds[0], buf.as_mut_ptr() as _, 64, 0) };
                    if r > 0 { received += 1; }
                }
            });

            handle.join().ok();
            if elapsed == 0 { continue; }
            let pps = n_datagrams as f64 / (elapsed as f64 / 1e9);
            samples.push(pps);
            emit_sample(writer, bench_id, Category::Ipc, 1, i, cfg.measure_iters, pps, MetricUnit::OpsPerSec, 0);
        }

        unsafe { libc::close(fds[0]); libc::close(fds[1]); }
        BenchResult::from_samples(samples, MetricUnit::OpsPerSec)
    }
}

// --- epoll wakeup latency ---

pub struct EpollWakeup;

impl Benchmark for EpollWakeup {
    fn id(&self)       -> &'static str { "ipc.epoll_wakeup_latency" }
    fn name(&self)     -> &'static str { "epoll_wait wakeup latency" }
    fn category(&self) -> Category     { Category::Ipc }
    fn config(&self)   -> BenchmarkConfig {
        BenchmarkConfig { warmup_iters: 200, measure_iters: 5_000, unit: MetricUnit::NsPerOp }
    }

    fn run(&self, writer: Option<&RingBufferWriter>, cancel: &AtomicBool, _cfg: &BenchConfig) -> BenchResult {
        let cfg = self.config();
        let bench_id = 20u16;
        let mut pipe_fds = [0i32; 2];
        unsafe { libc::pipe(pipe_fds.as_mut_ptr()) };
        let epfd = unsafe { libc::epoll_create1(0) };
        let mut ev = epoll_event {
            events: (EPOLLIN | EPOLLET) as u32,
            u64:    pipe_fds[0] as u64,
        };
        unsafe { libc::epoll_ctl(epfd, EPOLL_CTL_ADD, pipe_fds[0], &mut ev) };
        let mut samples = Vec::with_capacity(cfg.measure_iters as usize);
        let buf = [1u8; 1];

        for _ in 0..cfg.warmup_iters {
            unsafe {
                libc::write(pipe_fds[1], buf.as_ptr() as _, 1);
                let mut events = vec![epoll_event { events: 0, u64: 0 }; 1];
                libc::epoll_wait(epfd, events.as_mut_ptr(), 1, 100);
                let mut rb = [0u8; 1];
                libc::read(pipe_fds[0], rb.as_mut_ptr() as _, 1);
            }
        }

        for i in 0..cfg.measure_iters {
            if i % 500 == 0 && cancel.load(Ordering::Acquire) { break; }
            let elapsed = time_ns!({
                unsafe {
                    libc::write(pipe_fds[1], buf.as_ptr() as _, 1);
                    let mut events = [epoll_event { events: 0, u64: 0 }; 1];
                    libc::epoll_wait(epfd, events.as_mut_ptr(), 1, 100);
                    let mut rb = [0u8; 1];
                    libc::read(pipe_fds[0], rb.as_mut_ptr() as _, 1);
                }
            });
            let ns = elapsed as f64;
            samples.push(ns);
            emit_sample(writer, bench_id, Category::Ipc, 1, i, cfg.measure_iters, ns, MetricUnit::NsPerOp, 0);
        }

        unsafe {
            libc::close(epfd);
            libc::close(pipe_fds[0]);
            libc::close(pipe_fds[1]);
        }
        BenchResult::from_samples(samples, MetricUnit::NsPerOp)
    }
}

// --- epoll scalability (many fds) ---

pub struct EpollScalability;

impl Benchmark for EpollScalability {
    fn id(&self)       -> &'static str { "ipc.epoll_scalability" }
    fn name(&self)     -> &'static str { "epoll scalability (100 fds)" }
    fn category(&self) -> Category     { Category::Ipc }
    fn config(&self)   -> BenchmarkConfig {
        BenchmarkConfig { warmup_iters: 50, measure_iters: 2_000, unit: MetricUnit::UsPerOp }
    }

    fn run(&self, writer: Option<&RingBufferWriter>, cancel: &AtomicBool, _cfg: &BenchConfig) -> BenchResult {
        let cfg = self.config();
        let bench_id = 21u16;
        let n_fds = 100usize;
        let epfd = unsafe { libc::epoll_create1(0) };
        // Create N pipes and register read ends
        let mut pipes: Vec<[i32; 2]> = Vec::with_capacity(n_fds);
        for _ in 0..n_fds {
            let mut p = [0i32; 2];
            unsafe { libc::pipe(p.as_mut_ptr()) };
            pipes.push(p);
            let mut ev = epoll_event {
                events: (EPOLLIN | EPOLLET) as u32,
                u64:    p[0] as u64,
            };
            unsafe { libc::epoll_ctl(epfd, EPOLL_CTL_ADD, p[0], &mut ev) };
        }
        let mut samples = Vec::with_capacity(cfg.measure_iters as usize);
        let buf = [1u8; 1];
        let mut rb = [0u8; 1];

        for _ in 0..cfg.warmup_iters {
            // Write to a random fd, then wait
            unsafe {
                libc::write(pipes[0][1], buf.as_ptr() as _, 1);
                let mut events = [epoll_event { events: 0, u64: 0 }; 1];
                libc::epoll_wait(epfd, events.as_mut_ptr(), 1, 10);
                libc::read(pipes[0][0], rb.as_mut_ptr() as _, 1);
            }
        }

        for i in 0..cfg.measure_iters {
            if i % 200 == 0 && cancel.load(Ordering::Acquire) { break; }
            let fd_idx = (i as usize) % n_fds;
            let elapsed = time_ns!({
                unsafe {
                    libc::write(pipes[fd_idx][1], buf.as_ptr() as _, 1);
                    let mut events = [epoll_event { events: 0, u64: 0 }; 1];
                    libc::epoll_wait(epfd, events.as_mut_ptr(), 1, 10);
                    libc::read(pipes[fd_idx][0], rb.as_mut_ptr() as _, 1);
                }
            });
            let us = elapsed as f64 / 1000.0;
            samples.push(us);
            emit_sample(writer, bench_id, Category::Ipc, 1, i, cfg.measure_iters, us, MetricUnit::UsPerOp, 0);
        }

        unsafe {
            libc::close(epfd);
            for p in &pipes { libc::close(p[0]); libc::close(p[1]); }
        }
        BenchResult::from_samples(samples, MetricUnit::UsPerOp)
    }
}
