package io.github.iamlooper.benchsuite.ui.screens.runner

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.iamlooper.benchsuite.data.repository.DeviceRepository
import io.github.iamlooper.benchsuite.data.repository.RunRepository
import io.github.iamlooper.benchsuite.engine.BenchmarkEngine
import io.github.iamlooper.benchsuite.engine.EngineState
import io.github.iamlooper.benchsuite.engine.ProgressState
import io.github.iamlooper.benchsuite.proto.SuiteConfig
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RunnerViewModel @Inject constructor(
    private val engine: BenchmarkEngine,
    private val runRepository: RunRepository,
    private val deviceRepository: DeviceRepository,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    val engineState: StateFlow<EngineState>   = engine.state
    val progress: StateFlow<ProgressState>    = engine.progress
    val sparkline: StateFlow<List<Double>>    = engine.sparkline

    /**
     * Starts the benchmark suite and persists the result to Room when complete.
     *
     * @param onComplete Called with the local run UUID once the result is saved.
     */
    fun startRun(onComplete: (runId: String) -> Unit) {
        viewModelScope.launch {
            if (engineState.value != EngineState.IDLE) return@launch

            var completedRunId: String? = null
            val config = SuiteConfig.newBuilder()
                .setStoragePath(context.filesDir.absolutePath)
                .build()
            try {
                engine.startSuite(config)

                // Wait for a terminal state (driven by Choreographer in BenchmarkEngine).
                // UNINITIALIZED is included so that a teardown() during a live run unblocks
                // this suspension rather than waiting forever.
                val terminal = engine.state.first { state ->
                    state == EngineState.COMPLETED ||
                    state == EngineState.CANCELLED ||
                    state == EngineState.ERROR      ||
                    state == EngineState.UNINITIALIZED
                }

                if (terminal == EngineState.COMPLETED) {
                    val result = engine.fetchResults()
                    val device = deviceRepository.getDeviceInfo()
                    val enriched = result.copy(
                        deviceBrand = device.brand,
                        deviceModel = device.model,
                        deviceSoc   = device.soc,
                    )
                    runRepository.saveRun(enriched)
                    completedRunId = enriched.id
                }
            } finally {
                engine.resetToIdle()
            }
            completedRunId?.let(onComplete)
        }
    }

    /** Requests cancellation of the in-progress run. */
    fun cancelRun() {
        engine.requestCancel()
    }

    override fun onCleared() {
        super.onCleared()
        // viewModelScope is cancelled before onCleared() is called. Any startRun() coroutine is
        // mid-cancellation on the Main dispatcher queue. forceCancel() resets RUNNING to IDLE
        // synchronously. resetToIdle() handles CANCELLED/COMPLETED/ERROR so the next ViewModel
        // instance always starts from a clean IDLE state.
        engine.forceCancel()
        engine.resetToIdle()
    }
}
