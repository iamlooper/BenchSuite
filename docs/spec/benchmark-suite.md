# Section 4: Benchmark Suite

## 4.1 Universal Compatibility Contract

**Every benchmark must satisfy ALL of these requirements:**

1. Uses only POSIX/Linux APIs guaranteed available on all supported Android versions.
2. Never calls a syscall that could be blocked by Android's seccomp-bpf filter.
3. Never requires root, `sched_setaffinity`, or any capability beyond a normal app sandbox.
4. Uses only libc wrappers (never raw `syscall()`) to ensure vDSO and seccomp compatibility.
5. Operates only on app-private internal storage (`Context.getFilesDir()`).

**Explicitly excluded APIs:** `io_uring`, `signalfd`, `memfd_create`, `statx`, `sched_setaffinity`, `perf_event_open`, raw `syscall(__NR_*)`, `SIGUSR1`/`pthread_kill`.

## 4.2 Benchmark Categories

| # | Category | Benchmarks | What It Measures |
|---|---|---|---|
| 1 | **CPU & Syscall** | 5 | Syscall cost via libc, vDSO paths, thread lifecycle |
| 2 | **Memory** | 6 | Allocation, bandwidth, page faults, TLB, mmap |
| 3 | **Scheduler** | 5 | Wake latency, thread coordination, contention, fairness |
| 4 | **IPC** | 6 | Pipes, UNIX sockets, epoll, event notification |
| 5 | **Storage I/O** | 6 | Sequential/random file I/O, fsync, metadata ops, mmap I/O |
| 6 | **Network** | 4 | TCP/UDP loopback throughput and latency, epoll server |
| 7 | **Timers** | 3 | Sleep accuracy, clock resolution, wake jitter |

**Total: 35 benchmarks** across 7 categories.

## 4.3 Category 1: CPU & Syscall

| Benchmark ID | Description | Metric |
|---|---|---|
| `cpu.clock_gettime_libc` | `clock_gettime(CLOCK_MONOTONIC)` via libc vDSO | ns/call |
| `cpu.getpid_libc` | `getpid()` via libc | ns/call |
| `cpu.sched_yield` | `sched_yield()` voluntary CPU yield | ns/call |
| `cpu.thread_create_join` | `pthread_create()` + `pthread_join()` | µs/thread |
| `cpu.thread_context_switch` | Two-thread mutex+cond ping-pong | ns/context-switch |

## 4.4 Category 2: Memory

| Benchmark ID | Description | Metric |
|---|---|---|
| `mem.mmap_anon_fault` | Anonymous mmap + page-fault touch | ns/page-fault |
| `mem.mmap_munmap_cycle` | Repeated mmap/munmap cycles | µs/cycle |
| `mem.memcpy_bandwidth` | Large memcpy between mmap'd regions | GB/s |
| `mem.stride_sweep` | Pointer-chasing array walk at varying strides | ns/access |
| `mem.malloc_free` | `malloc()` + `free()` across various sizes | ns/alloc-free |
| `mem.file_backed_mmap` | File mmap + random page reads | µs/page-read |

## 4.5 Category 3: Scheduler

| Benchmark ID | Description | Metric |
|---|---|---|
| `sched.mutex_pingpong` | Two-thread mutex+cond ping-pong | ns/roundtrip |
| `sched.barrier_latency` | N-thread pthread_barrier_wait | µs/barrier |
| `sched.rwlock_contention` | N readers + 1 writer on pthread_rwlock | ops/sec |
| `sched.yield_storm` | N threads tight sched_yield loops | yields/sec |
| `sched.message_flood` | Concurrent UNIX socket message passing | msgs/sec |

## 4.6 Category 4: IPC

| Benchmark ID | Description | Metric |
|---|---|---|
| `ipc.pipe_throughput` | Pipe write/read between two threads | MB/s |
| `ipc.pipe_latency` | Pipe ping-pong latency | µs/op |
| `ipc.unix_socket_stream` | UNIX SOCK_STREAM throughput + latency | MB/s + µs |
| `ipc.unix_socket_dgram` | UNIX SOCK_DGRAM datagram rate | msgs/sec |
| `ipc.epoll_wakeup` | epoll event notification overhead | ns/wakeup |
| `ipc.epoll_scalability` | epoll dispatch vs. FD count | events/sec |

## 4.7 Category 5: Storage I/O

All storage benchmarks operate on **app-private internal storage** (`Context.getFilesDir()`).

| Benchmark ID | Description | Metric |
|---|---|---|
| `io.seq_write` | Sequential buffered writes + fdatasync | MB/s |
| `io.seq_read` | Sequential buffered reads (cold + warm) | MB/s |
| `io.rand_write` | Random 4K/16K block writes | IOPS |
| `io.rand_read` | Random 4K/16K block reads | IOPS |
| `io.mmap_file_rw` | Memory-mapped file I/O + msync | µs/msync |
| `io.metadata_ops` | create/stat/rename/unlink throughput | ops/sec |

## 4.8 Category 6: Network (Loopback)

All network benchmarks use loopback (`127.0.0.1`/`::1`), no special permissions needed.

| Benchmark ID | Description | Metric |
|---|---|---|
| `net.tcp_throughput` | TCP loopback bandwidth | MB/s |
| `net.tcp_latency` | TCP ping-pong request-response | µs/roundtrip |
| `net.udp_pps` | UDP loopback packets-per-second | pkts/sec |
| `net.epoll_server` | Single-threaded epoll TCP server | reqs/sec |

## 4.9 Category 7: Timers & Timekeeping

| Benchmark ID | Description | Metric |
|---|---|---|
| `timer.nanosleep_jitter` | `clock_nanosleep()` wake jitter | ns jitter p50/p99 |
| `timer.nanosleep_accuracy` | `clock_nanosleep()` overshoot | ns overshoot |
| `timer.clock_resolution` | Effective resolution of clock sources | ns resolution |

## 4.10 Benchmark Execution Model

Each benchmark follows: **Warm-up → Measure → Cool-down → Next Benchmark**

- Warm-up: 10% of iterations, results discarded
- Measure: full iterations recorded
- Cool-down: 100ms sleep between benchmarks
- Statistics: p50, p99, best-of-rounds

## 4.11 Suite Execution

Each run executes all 35 registered benchmarks across all 7 categories.
