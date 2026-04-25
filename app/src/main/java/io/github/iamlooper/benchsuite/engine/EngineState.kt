package io.github.iamlooper.benchsuite.engine

/** Discrete states of the benchmark engine lifecycle. */
enum class EngineState {
    /** Engine not yet initialised (before nativeInit). */
    UNINITIALIZED,

    /** Engine initialised, ring buffer ready; no benchmark running. */
    IDLE,

    /** A benchmark suite or single benchmark is executing. */
    RUNNING,

    /** All benchmarks completed; results available. */
    COMPLETED,

    /** A cancellation was requested and acknowledged by Rust. */
    CANCELLED,

    /** A non-recoverable error occurred; engine must be destroyed and re-initialised. */
    ERROR,
}
