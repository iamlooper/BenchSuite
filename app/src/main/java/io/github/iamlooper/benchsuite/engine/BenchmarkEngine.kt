package io.github.iamlooper.benchsuite.engine

import android.view.Choreographer
import io.github.iamlooper.benchsuite.BuildConfig
import io.github.iamlooper.benchsuite.data.model.BenchmarkResult
import io.github.iamlooper.benchsuite.data.model.Category
import io.github.iamlooper.benchsuite.data.model.CategoryScore
import io.github.iamlooper.benchsuite.data.model.MetricUnit
import io.github.iamlooper.benchsuite.data.model.RunResult
import io.github.iamlooper.benchsuite.data.model.StabilityRating
import io.github.iamlooper.benchsuite.proto.SuiteConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BenchmarkEngine orchestrates the benchmark lifecycle:
 *   - Initialises [BenchmarkBridge] (JNI + ring buffer)
 *   - Drives the [RingBufferReader] via [Choreographer] at ~60 Hz
 *   - Emits real-time [ProgressState] via [StateFlow]
 *   - Fetches and converts final results to domain models
 *
 * This is a process-level singleton injected by Hilt. The engine is initialised once in
 * [App] and torn down when the process exits.
 */
@Singleton
class BenchmarkEngine @Inject constructor() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    val bridge = BenchmarkBridge()
    private val reader = RingBufferReader(bridge.ringBuffer)

    // Public state

    private val _state   = MutableStateFlow<EngineState>(EngineState.UNINITIALIZED)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private val _progress = MutableStateFlow(ProgressState())
    val progress: StateFlow<ProgressState> = _progress.asStateFlow()

    // Rolling 200-sample sparkline for the currently running benchmark
    private val _sparkline = MutableStateFlow<List<Double>>(emptyList())
    val sparkline: StateFlow<List<Double>> = _sparkline.asStateFlow()

    // Choreographer handle for cancellation
    private var choreographerCallback: Choreographer.FrameCallback? = null

    // Lifecycle

    /**
     * Initialises the JNI bridge and ring buffer. Must be called once on app startup
     * before any [startSuite] or [startSingle] call. Safe to re-call after [teardown].
     */
    suspend fun initialize() {
        if (_state.value != EngineState.UNINITIALIZED) return
        try {
            bridge.init()
            _state.value = EngineState.IDLE
        } catch (t: Throwable) {
            bridge.cleanupAfterFailedInit()
            throw t
        }
    }

    /**
     * Starts a benchmark suite run.
     *
     * @param config [SuiteConfig] with storage_path.
     */
    suspend fun startSuite(config: SuiteConfig) {
        check(_state.value == EngineState.IDLE) {
            "Engine must be IDLE to start a run (current: ${_state.value})"
        }
        _progress.value = ProgressState()
        _sparkline.value = emptyList()
        _state.value = EngineState.RUNNING
        try {
            // Wait for any in-flight Rust task from a previous run to finish writing before
            // resetting the ring buffer read cursor. Records committed by an old task after
            // the reset point bleed into this session's Choreographer window. Reset happens
            // before starting the new Rust task so the cursor sits at a clean boundary
            // between runs.
            awaitPreviousRunDrainAndReset()
            // Abort if a cancel arrived during the drain wait.
            if (_state.value != EngineState.RUNNING) return
            bridge.startSuite(config)
            startChoreographerPolling()
        } catch (t: Throwable) {
            stopChoreographerPolling()
            _progress.value = ProgressState()
            _sparkline.value = emptyList()
            _state.value = EngineState.IDLE
            throw t
        }
    }

    /**
     * Starts a single benchmark by numeric id.
     *
     * @param benchId   Rust registry id.
     * @param config    [SuiteConfig] with storage_path populated.
     */
    suspend fun startSingle(benchId: Int, config: SuiteConfig) {
        check(_state.value == EngineState.IDLE) {
            "Engine must be IDLE to start a run (current: ${_state.value})"
        }
        _progress.value = ProgressState()
        _sparkline.value = emptyList()
        _state.value = EngineState.RUNNING
        try {
            awaitPreviousRunDrainAndReset()
            if (_state.value != EngineState.RUNNING) return
            bridge.startSingle(benchId, config)
            startChoreographerPolling()
        } catch (t: Throwable) {
            stopChoreographerPolling()
            _progress.value = ProgressState()
            _sparkline.value = emptyList()
            _state.value = EngineState.IDLE
            throw t
        }
    }

    /**
     * Requests cancellation. Stops the Choreographer immediately so a concurrent
     * ring-buffer COMPLETE record cannot transition state to COMPLETED while the cancel
     * is in flight, then signals Rust via a fire-and-forget JNI call.
     *
     * Not suspend: all work is synchronous on the calling (main) thread so callers do
     * not need a coroutine scope.
     */
    fun requestCancel() {
        if (_state.value != EngineState.RUNNING) return
        // Stop polling first. If we called bridge.cancel() (suspend, up to 30 s) first,
        // the Choreographer could race to detect a COMPLETE record and transition to
        // COMPLETED before we ever set CANCELLED.
        stopChoreographerPolling()
        _state.value = EngineState.CANCELLED
        // Fire-and-forget cancel to Rust. The drain wait in the next startSuite/startSingle
        // call tolerates the async gap between this signal and Rust actually stopping.
        bridge.cancelImmediate()
    }

    /**
     * Immediately stops any in-progress run without suspending. Safe to call from
     * [androidx.lifecycle.ViewModel.onCleared] where no coroutine scope is available.
     *
     * Unlike [requestCancel], this does not wait for Rust to acknowledge. It fires the cancel
     * JNI call and synchronously resets the engine to IDLE so subsequent runs can proceed.
     * Any still-running Rust task will observe the cancel flag via its own Arc clone and exit.
     */
    fun forceCancel() {
        if (_state.value != EngineState.RUNNING) return
        bridge.cancelImmediate()
        stopChoreographerPolling()
        _progress.value = ProgressState()
        _sparkline.value = emptyList()
        _state.value = EngineState.IDLE
    }

    /**
     * Fetches final results from Rust and converts to domain [RunResult].
     * Call only after [state] has transitioned to [EngineState.COMPLETED].
     */
    suspend fun fetchResults(): RunResult {
        val resp     = bridge.getResults()
        val suite    = resp.results
        val metadata = suite.metadata

        val categories = suite.categoriesList.map { catResult ->
            val benchResults = catResult.benchmarksList.map { b ->
                BenchmarkResult(
                    id             = b.id,
                    displayName    = b.name,
                    category       = Category.fromString(catResult.category),
                    unit           = MetricUnit.fromString(b.unit),
                    metricP50      = b.metricP50.finiteOrZero(),
                    metricP99      = b.metricP99.finiteOrZero(),
                    metricBest     = b.metricBest.finiteOrZero(),
                    metricMean     = b.metricMean.finiteOrZero(),
                    throughput     = b.throughput.finiteOrZero(),
                    variancePct    = b.variancePct.finiteOrZero(),
                    score          = null,  // Populated by the server after population bootstrap.
                )
            }
            CategoryScore(
                category = Category.fromString(catResult.category),
                score    = computeScoreFromThroughputs(
                    benchResults.mapNotNull { benchmark ->
                        benchmark.throughput.takeIf { throughput ->
                            throughput > 0.0
                        }
                    },
                ),
                benchmarks = benchResults,
            )
        }.sortedBy { it.category.ordinal }

        val allThroughputs = categories.flatMap { cat ->
            cat.benchmarks.mapNotNull { b ->
                b.throughput.takeIf { throughput ->
                    throughput > 0.0
                }
            }
        }
        val computedOverallScore = computeScoreFromThroughputs(allThroughputs)

        // Compute stability rating: prefer metadata.stabilityRating from native engine,
        // fall back to computing from individual benchmark variance, or null if unavailable.
        // Uses MEDIAN of per-benchmark CV% (resistant to outlier benchmarks) with thresholds
        // calibrated for real-world system benchmark variance on Android devices.
        val allVariances = categories.flatMap { cat ->
            cat.benchmarks.map { it.variancePct }
        }
        val computedStability: StabilityRating? = when {
            metadata.stabilityRating.isNotBlank() -> StabilityRating.fromString(metadata.stabilityRating)
            allVariances.isNotEmpty() -> {
                val sorted = allVariances.sorted()
                val medianVariance = if (sorted.size % 2 == 0) {
                    (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
                } else {
                    sorted[sorted.size / 2]
                }
                when {
                    medianVariance < 8.0  -> StabilityRating.EXCELLENT
                    medianVariance < 20.0 -> StabilityRating.GOOD
                    medianVariance < 35.0 -> StabilityRating.FAIR
                    else                  -> StabilityRating.UNSTABLE
                }
            }
            else -> null
        }

        return RunResult(
            id              = UUID.randomUUID().toString(),
            deviceBrand     = suite.device?.brand.orEmpty(),
            deviceModel     = suite.device?.model.orEmpty(),
            deviceSoc       = suite.device?.soc.orEmpty(),
            appVersion      = BuildConfig.VERSION_NAME,
            overallScore    = computedOverallScore,
            stabilityRating = computedStability,
            categories      = categories,
            isUploaded      = false,
            startedAt       = metadata.startedAtMs,
            completedAt     = metadata.completedAtMs,
        )
    }

    private fun computeScoreFromThroughputs(throughputs: List<Double>): Double? {
        if (throughputs.isEmpty()) return null
        val logSum = throughputs.sumOf { ln(it) }
        val geoMean = exp(logSum / throughputs.size)
        return (log10(geoMean) * 200.0).coerceIn(0.0, 5000.0)
    }

    /**
     * Destroys the native bridge. Call [initialize] to re-use the engine after this.
     * In practice this is only called when the app goes to background or the process is exiting.
     * Cancels any in-progress run before tearing down so the Rust task receives the cancel
     * signal rather than writing to an orphaned ring buffer.
     */
    fun teardown() {
        forceCancel()
        stopChoreographerPolling()
        bridge.destroy()
        _state.value = EngineState.UNINITIALIZED
    }

    /**
     * Resets the engine to IDLE after a completed, cancelled, or errored run.
     * Must be called before starting a new run.
     *
     * Progress and sparkline are intentionally NOT cleared here; they are cleared at the
     * start of the next run in startSuite/startSingle so the screen retains the last-known
     * state during the brief window between the terminal state and navigation.
     */
    fun resetToIdle() {
        if (_state.value == EngineState.COMPLETED ||
            _state.value == EngineState.CANCELLED ||
            _state.value == EngineState.ERROR
        ) {
            stopChoreographerPolling()
            _state.value = EngineState.IDLE
        }
    }

    // Choreographer ring buffer polling

    /**
     * Spins until the Rust engine state leaves "running" (1), then resets the ring buffer
     * read cursor to the current write position. This ensures all records from a previous
     * run are behind the cursor and cannot be consumed by the incoming Choreographer session.
     *
     * The 2-second timeout is a safety bound. In practice the cancel signal propagates to
     * Rust within one benchmark iteration (well under 500 ms for all current benchmarks).
     * If the timeout expires, we proceed with the reset anyway; the drain wait having
     * trimmed most of the old-run window is better than not waiting at all.
     */
    private suspend fun awaitPreviousRunDrainAndReset() {
        val deadlineMs = System.currentTimeMillis() + 2_000L
        while (reader.engineState() == 1 && System.currentTimeMillis() < deadlineMs) {
            delay(50L)
        }
        reader.resetToCurrentWriteIndex()
    }

    private fun startChoreographerPolling() {
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                pollRingBuffer()
                // Re-register only if still running
                if (_state.value == EngineState.RUNNING) {
                    Choreographer.getInstance().postFrameCallback(this)
                }
            }
        }
        choreographerCallback = callback
        Choreographer.getInstance().postFrameCallback(callback)
    }

    private fun stopChoreographerPolling() {
        choreographerCallback?.let {
            Choreographer.getInstance().removeFrameCallback(it)
        }
        choreographerCallback = null
    }

    private fun pollRingBuffer() {
        val records = reader.poll()
        if (records.isEmpty()) {
            if (reader.engineState() == 2 && _state.value == EngineState.RUNNING) {
                stopChoreographerPolling()
                // pollRingBuffer runs on the main thread via Choreographer, so direct assignment
                // is safe and avoids a race where the next frame re-registers before the
                // async scope.launch dispatch propagates the COMPLETED state.
                _state.value = EngineState.COMPLETED
            }
            return
        }

        val currentProgress = _progress.value
        var totalBenchmarks    = currentProgress.totalBenchmarks
        var completedBenchmarks = currentProgress.completedBenchmarks
        var currentBenchId     = currentProgress.currentBenchId
        var currentPhase       = currentProgress.currentPhase
        var currentIter        = currentProgress.currentIter
        var totalIter          = currentProgress.totalIter
        val recentSamples      = _sparkline.value.toMutableList()

        for (record in records) {
            when (record.recordType) {
                RecordType.PROGRESS -> {
                    val benchChanged = currentBenchId != record.benchId
                    currentBenchId      = record.benchId
                    completedBenchmarks = record.currentIter
                    totalBenchmarks     = record.totalIter
                    currentPhase        = 0
                    currentIter         = 0
                    totalIter           = 0
                    if (benchChanged) recentSamples.clear()
                }
                RecordType.SAMPLE -> {
                    currentPhase = record.phase
                    currentIter  = record.currentIter
                    totalIter    = record.totalIter
                    recentSamples.add(record.valuePrimary)
                    if (recentSamples.size > 200) {
                        recentSamples.removeAt(0)
                    }
                }
                RecordType.COMPLETE -> {
                    stopChoreographerPolling()
                    completedBenchmarks = totalBenchmarks
                    _progress.value = currentProgress.copy(
                        totalBenchmarks     = totalBenchmarks,
                        completedBenchmarks = completedBenchmarks,
                        currentBenchId      = currentBenchId,
                        currentPhase        = currentPhase,
                        currentIter         = currentIter,
                        totalIter           = totalIter,
                    )
                    _sparkline.value = recentSamples
                    _state.value = EngineState.COMPLETED
                    return
                }
                else -> { /* LOG records are currently ignored */ }
            }
        }

        _progress.value = currentProgress.copy(
            totalBenchmarks     = totalBenchmarks,
            completedBenchmarks = completedBenchmarks,
            currentBenchId      = currentBenchId,
            currentPhase        = currentPhase,
            currentIter         = currentIter,
            totalIter           = totalIter,
        )
        _sparkline.value = recentSamples
    }
}

/**
 * Snapshot of live benchmark run progress emitted by [BenchmarkEngine].
 *
 * @param totalBenchmarks    Total number of benchmarks in this run mode.
 * @param completedBenchmarks Number of benchmarks that have reached their Complete record.
 * @param currentBenchId     Numeric [bench_id] of the currently executing benchmark.
 * @param currentPhase       Phase constant from [Phase] (warmup/measure/cooldown).
 * @param currentIter        Current iteration within the active phase.
 * @param totalIter          Total iterations in the active phase.
 */
data class ProgressState(
    val totalBenchmarks: Int    = 0,
    val completedBenchmarks: Int = 0,
    val currentBenchId: Int     = -1,
    val currentPhase: Int       = 0,
    val currentIter: Int        = 0,
    val totalIter: Int          = 0,
) {
    val overallFraction: Float
        get() = if (totalBenchmarks == 0) 0f
                else completedBenchmarks.toFloat() / totalBenchmarks

    val benchmarkFraction: Float
        get() = if (totalIter == 0) 0f
                else currentIter.toFloat() / totalIter
}

// Defense-in-depth: replaces any non-finite protobuf double at the fetch boundary so
// nothing downstream (Room, UI, scoring) ever sees an invalid value.
private fun Double.finiteOrZero(): Double = if (isFinite()) this else 0.0
