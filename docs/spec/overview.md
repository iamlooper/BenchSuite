# Section 1: Overview

## 1.1 What is BenchSuite?

BenchSuite is an Android benchmarking application that measures device performance by directly exercising **Linux kernel subsystems and POSIX APIs** - syscalls, scheduler primitives, IPC mechanisms, filesystem operations, memory management, and network stack internals. Unlike synthetic benchmarks that run artificial workloads designed to produce a "big number," BenchSuite measures **how fast your kernel actually responds** to real system operations that every app depends on.

## 1.2 Core Principles

| Principle | Description |
|---|---|
| **Kernel-native** | Every benchmark exercises a real Linux syscall, file descriptor operation, or kernel primitive. No synthetic compute loops. |
| **Universal** | Every benchmark uses only POSIX/Linux APIs guaranteed to be available on all supported Android versions, without root. Zero chance of seccomp kills or ENOSYS. |
| **Transparent** | All benchmark logic is open. Scores map directly to measurable quantities (ns/op, MB/s, ops/sec). |
| **Reproducible** | Warm-up phases, statistical aggregation (p50/p99), and variance detection ensure consistent results. |
| **Real-time** | Benchmark samples stream live from Rust → Kotlin via a lock-free SPSC ring buffer. Commands and results use an async Protobuf Envelope bridge (Tokio + `CompletableDeferred`) for clean request/response semantics. |
| **Beautiful** | Material Design 3 Expressive UI with animated progress, radar charts, and a satisfying visual weight. |
| **Competitive** | Upload scores to a global leaderboard powered by Supabase. Compare your device against the world. |

## 1.3 Tech Stack

| Layer | Technology |
|---|---|
| **UI** | Kotlin, Jetpack Compose, Material Design 3 Expressive |
| **App Logic** | Kotlin, Kotlin Coroutines, Hilt (DI) |
| **Native Engine** | Rust (benchmarks, scoring, ring buffer, Tokio async runtime, Protobuf Envelope dispatch) |
| **Bridge** | Two-layer JNI: (1) Async Protobuf Envelope (`jbyteArray` + `jlong` handle + Tokio + `CompletableDeferred`) for control/results; (2) lock-free SPSC ring buffer over `DirectByteBuffer` for high-frequency progress streaming |
| **Backend** | Supabase (PostgreSQL + PostgREST + RLS) - free tier, no auth |
| **Build** | Gradle (Kotlin DSL), Cargo via `cargo-ndk`, GitHub Actions CI |

## 1.4 Supported ABIs

- `arm64-v8a` (primary target, 99%+ of modern devices)
- `armeabi-v7a` (legacy support)
- `x86_64` (emulator/ChromeOS)
- `x86` (older emulators/devices)

