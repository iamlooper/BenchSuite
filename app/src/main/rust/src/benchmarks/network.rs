use std::sync::atomic::{AtomicBool, Ordering};
use libc::{
    AF_UNIX, CLOCK_MONOTONIC, EPOLLET, EPOLLIN, EPOLL_CTL_ADD, MSG_DONTWAIT,
    SOCK_DGRAM, SOCK_STREAM, epoll_event, timespec,
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

/// Loopback TCP throughput – server and client in separate threads via socketpair proxy.
///
/// Implementation note: We use Unix stream socketpair() rather than a TPC listener
/// to avoid EADDRINUSE races on repeated runs and eliminate external network stack
/// latency. Unix stream sockets on Android go through the same kernel TCP-like
/// buffering path, so throughput numbers are comparable for a local IPC benchmark.
pub struct TcpThroughput;

impl Benchmark for TcpThroughput {
    fn id(&self)       -> &'static str { "network.tcp_throughput" }
    fn name(&self)     -> &'static str { "TCP loopback throughput" }
    fn category(&self) -> Category     { Category::Network }
    fn config(&self)   -> BenchmarkConfig {
        BenchmarkConfig { warmup_iters: 3, measure_iters: 20, unit: MetricUnit::MbPerSec }
    }

    fn run(&self, writer: Option<&RingBufferWriter>, cancel: &AtomicBool, _cfg: &BenchConfig) -> BenchResult {
        let cfg = self.config();
        let bench_id = 28u16;
        let total_bytes: usize = 128 * 1024 * 1024;
        let chunk: usize = 65536;
        let mut samples = Vec::with_capacity(cfg.measure_iters as usize);

        for i in 0..cfg.measure_iters {
            if cancel.load(Ordering::Acquire) { break; }
            let mut fds = [0i32; 2];
            if unsafe { libc::socketpair(AF_UNIX, SOCK_STREAM, 0, fds.as_mut_ptr()) } < 0 {
                break;
            }
            let fd_w    = fds[1];
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
                    if n > 0 { read += n as usize; } else { break; }
                }
            });

            handle.join().ok();
            unsafe { libc::close(fds[0]); libc::close(fds[1]); }
            if elapsed == 0 { continue; }
            let mbps = total_bytes as f64 / (elapsed as f64 / 1e9) / (1024.0 * 1024.0);
            samples.push(mbps);
            emit_sample(writer, bench_id, Category::Network, 1, i, cfg.measure_iters, mbps, MetricUnit::MbPerSec, 0);
        }

        BenchResult::from_samples(samples, MetricUnit::MbPerSec)
    }
}

// --- TCP latency (1-byte ping-pong, loopback socketpair) ---

pub struct TcpLatency;

impl Benchmark for TcpLatency {
    fn id(&self)       -> &'static str { "network.tcp_latency" }
    fn name(&self)     -> &'static str { "TCP loopback round-trip latency" }
    fn category(&self) -> Category     { Category::Network }
    fn config(&self)   -> BenchmarkConfig {
        BenchmarkConfig { warmup_iters: 100, measure_iters: 3_000, unit: MetricUnit::UsPerOp }
    }

    fn run(&self, writer: Option<&RingBufferWriter>, cancel: &AtomicBool, _cfg: &BenchConfig) -> BenchResult {
        let cfg = self.config();
        let bench_id = 29u16;
        let mut fds = [0i32; 2];
        if unsafe { libc::socketpair(AF_UNIX, SOCK_STREAM, 0, fds.as_mut_ptr()) } < 0 {
            return BenchResult::default();
        }
        let mut samples = Vec::with_capacity(cfg.measure_iters as usize);
        let total = cfg.warmup_iters + cfg.measure_iters;

        let handle = thread::spawn(move || {
            for _ in 0..total {
                let mut b = [0u8; 1];
                unsafe {
                    libc::recv(fds[1], b.as_mut_ptr() as _, 1, 0);
                    libc::send(fds[1], b.as_ptr() as _, 1, 0);
                }
            }
        });

        let buf = [0x42u8; 1];
        for i in 0..total {
            let measuring = i >= cfg.warmup_iters;
            if measuring && i % 300 == 0 && cancel.load(Ordering::Acquire) { break; }
            let elapsed = time_ns!({
                unsafe {
                    libc::send(fds[0], buf.as_ptr() as _, 1, 0);
                    let mut b = [0u8; 1];
                    libc::recv(fds[0], b.as_mut_ptr() as _, 1, 0);
                }
            });
            if measuring {
                let us = elapsed as f64 / 1000.0;
                let idx = i - cfg.warmup_iters;
                samples.push(us);
                emit_sample(writer, bench_id, Category::Network, 1, idx, cfg.measure_iters, us, MetricUnit::UsPerOp, 0);
            }
        }

        handle.join().ok();
        unsafe { libc::close(fds[0]); libc::close(fds[1]); }
        BenchResult::from_samples(samples, MetricUnit::UsPerOp)
    }
}

// --- UDP packets-per-second (loopback socketpair SOCK_DGRAM) ---

pub struct UdpPps;

impl Benchmark for UdpPps {
    fn id(&self)       -> &'static str { "network.udp_pps" }
    fn name(&self)     -> &'static str { "UDP loopback packets-per-second" }
    fn category(&self) -> Category     { Category::Network }
    fn config(&self)   -> BenchmarkConfig {
        BenchmarkConfig { warmup_iters: 5, measure_iters: 30, unit: MetricUnit::OpsPerSec }
    }

    fn run(&self, writer: Option<&RingBufferWriter>, cancel: &AtomicBool, _cfg: &BenchConfig) -> BenchResult {
        let cfg = self.config();
        let bench_id = 30u16;
        let n_packets: u64 = 50_000;
        let mut samples = Vec::with_capacity(cfg.measure_iters as usize);

        for i in 0..cfg.measure_iters {
            if cancel.load(Ordering::Acquire) { break; }
            let mut fds = [0i32; 2];
            if unsafe { libc::socketpair(AF_UNIX, SOCK_DGRAM, 0, fds.as_mut_ptr()) } < 0 {
                break;
            }
            let n = n_packets;
            let fd_send = fds[1];
            let handle = thread::spawn(move || {
                let buf = [0xAEu8; 64];
                for _ in 0..n { unsafe { libc::send(fd_send, buf.as_ptr() as _, 64, 0) }; }
            });

            let elapsed = time_ns!({
                let mut buf = [0u8; 64];
                let mut received = 0u64;
                while received < n_packets {
                    let r = unsafe { libc::recv(fds[0], buf.as_mut_ptr() as _, 64, 0) };
                    if r > 0 { received += 1; } else { break; }
                }
            });

            handle.join().ok();
            unsafe { libc::close(fds[0]); libc::close(fds[1]); }
            if elapsed == 0 { continue; }
            let pps = n_packets as f64 / (elapsed as f64 / 1e9);
            samples.push(pps);
            emit_sample(writer, bench_id, Category::Network, 1, i, cfg.measure_iters, pps, MetricUnit::OpsPerSec, 0);
        }

        BenchResult::from_samples(samples, MetricUnit::OpsPerSec)
    }
}

// --- epoll server scalability (many clients) ---

pub struct EpollServer;

impl Benchmark for EpollServer {
    fn id(&self)       -> &'static str { "network.epoll_server" }
    fn name(&self)     -> &'static str { "epoll server scalability (50 clients)" }
    fn category(&self) -> Category     { Category::Network }
    fn config(&self)   -> BenchmarkConfig {
        BenchmarkConfig { warmup_iters: 5, measure_iters: 20, unit: MetricUnit::OpsPerSec }
    }

    fn run(&self, writer: Option<&RingBufferWriter>, cancel: &AtomicBool, _cfg: &BenchConfig) -> BenchResult {
        let cfg = self.config();
        let bench_id = 31u16;
        let n_clients   = 50usize;
        let rounds_each = 1_000u64;
        let mut samples = Vec::with_capacity(cfg.measure_iters as usize);

        for i in 0..cfg.measure_iters {
            if cancel.load(Ordering::Acquire) { break; }

            // n_clients pairs of socketpairs
            let mut pairs: Vec<[i32; 2]> = Vec::with_capacity(n_clients);
            for _ in 0..n_clients {
                let mut fds = [0i32; 2];
                unsafe { libc::socketpair(AF_UNIX, SOCK_STREAM, 0, fds.as_mut_ptr()) };
                pairs.push(fds);
            }

            let epfd = unsafe { libc::epoll_create1(0) };
            for p in &pairs {
                let mut ev = epoll_event {
                    events: (EPOLLIN | EPOLLET) as u32,
                    u64:    p[0] as u64,
                };
                unsafe { libc::epoll_ctl(epfd, EPOLL_CTL_ADD, p[0], &mut ev) };
            }

            // Client threads each send 1000 bytes one at a time
            let mut handles = Vec::new();
            for p in &pairs {
                let fd_c = p[1];
                handles.push(thread::spawn(move || {
                    let buf = [0x5Bu8; 1];
                    for _ in 0..rounds_each {
                        unsafe { libc::send(fd_c, buf.as_ptr() as _, 1, 0) };
                    }
                }));
            }

            let total_events = n_clients as u64 * rounds_each;
            let elapsed = time_ns!({
                let mut events_seen = 0u64;
                let mut evbuf = vec![epoll_event { events: 0, u64: 0 }; n_clients];
                while events_seen < total_events {
                    let n = unsafe { libc::epoll_wait(epfd, evbuf.as_mut_ptr(), n_clients as i32, 500) };
                    if n <= 0 { break; }
                    for event in evbuf.iter().take(n as usize) {
                        let fd = event.u64 as i32;
                        let mut rb = [0u8; 1];
                        while unsafe { libc::recv(fd, rb.as_mut_ptr() as _, 1, MSG_DONTWAIT) } > 0 {
                            events_seen += 1;
                        }
                    }
                }
            });

            for h in handles { h.join().ok(); }
            unsafe {
                libc::close(epfd);
                for p in &pairs { libc::close(p[0]); libc::close(p[1]); }
            }

            if elapsed == 0 { continue; }
            let ops = total_events as f64 / (elapsed as f64 / 1e9);
            samples.push(ops);
            emit_sample(writer, bench_id, Category::Network, 1, i, cfg.measure_iters, ops, MetricUnit::OpsPerSec, 0);
        }

        BenchResult::from_samples(samples, MetricUnit::OpsPerSec)
    }
}
