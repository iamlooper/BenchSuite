package io.github.iamlooper.benchsuite.data.model

/** Benchmark category as understood by the Kotlin domain layer. */
enum class Category(val id: String, val displayName: String, val description: String) {
    CPU("cpu", "CPU & Syscall", "System call overhead, CPU pipeline, and clock precision benchmarks."),
    MEMORY("memory", "Memory", "Memory bandwidth, latency, and cache hierarchy benchmarks."),
    SCHEDULER("scheduler", "Scheduler", "Thread wake-up latency, context switch cost, and task scheduling benchmarks."),
    IPC("ipc", "IPC", "Inter-process communication throughput via pipes, sockets, and shared memory."),
    IO("io", "Storage I/O", "Sequential and random read/write throughput and IOPS for local storage."),
    NETWORK("network", "Network", "Loopback and local socket network throughput and latency benchmarks."),
    TIMER("timer", "Timers", "High-resolution timer accuracy, sleep precision, and event loop latency benchmarks.");

    companion object {
        fun fromStringOrNull(value: String): Category? =
            entries.firstOrNull { it.id == value }

        fun fromString(value: String): Category =
            fromStringOrNull(value) ?: CPU
    }
}
