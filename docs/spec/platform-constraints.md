# Section 10: Platform Constraints & Universal Compatibility

## 10.1 Compatibility Contract

Every benchmark must work on all supported devices (minSdk 24, Android 7.0+) without root or special permissions.

### Allowed APIs

Only POSIX/Linux APIs that are:
1. Available on all Android versions since API 24
2. Not blocked by Android's default seccomp-bpf filter
3. Accessible without root or special capabilities
4. Accessible through libc wrappers (never raw `syscall()`)

### Explicitly Excluded APIs

| API | Reason |
|---|---|
| `io_uring` | Blocked by seccomp on Android 12+ |
| `signalfd` | Not universally allowed across OEM seccomp profiles |
| `memfd_create` | ENOSYS on older kernels |
| `statx` | ENOSYS on older kernels |
| `sched_setaffinity` | EPERM in app sandbox cgroups |
| `perf_event_open` | Blocked by seccomp |
| Raw `syscall(__NR_*)` | Bypasses vDSO and risks seccomp kills |
| `SIGUSR1`/`pthread_kill` for benchmarking | Signal handling fragile across runtimes |

## 10.2 Storage

All file I/O benchmarks operate exclusively on **app-private internal storage** (`Context.getFilesDir()`). No `READ_EXTERNAL_STORAGE`, no `WRITE_EXTERNAL_STORAGE`.

## 10.3 Network

Network benchmarks use only loopback (`127.0.0.1`, `::1`). No `INTERNET` permission beyond what all Android apps have by default.

## 10.4 Thread Affinity

`sched_setaffinity` is unavailable in the app sandbox. Benchmarks are designed to produce meaningful results without CPU pinning by using sufficiently long measurement phases and statistical aggregation (p50/p99/best-of-rounds).

## 10.5 vDSO Fast Paths

Benchmarks like `cpu.clock_gettime_libc` intentionally use the vDSO-backed libc call (no kernel entry on most devices). This is correct behavior - the benchmark measures the real cost of the API path that apps actually use.
