package io.github.iamlooper.benchsuite.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.iamlooper.benchsuite.data.local.LocalRunEntity
import io.github.iamlooper.benchsuite.data.repository.RunRepository
import io.github.iamlooper.benchsuite.engine.BenchmarkEngine
import io.github.iamlooper.benchsuite.engine.EngineState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val runRepository: RunRepository,
    private val engine: BenchmarkEngine,
) : ViewModel() {

    /** All local runs ordered newest-first. */
    val runs: StateFlow<List<LocalRunEntity>> = runRepository
        .getAllRuns()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Current engine lifecycle state (UNINITIALIZED → IDLE → ...). */
    val engineState: StateFlow<EngineState> = engine.state

    /** Deletes a single local run and its associated results/scores. */
    fun deleteRun(runId: String) {
        viewModelScope.launch { runRepository.deleteRun(runId) }
    }

    /** Deletes all local runs and their associated results/scores. */
    fun clearAllRuns() {
        viewModelScope.launch { runRepository.deleteAllRuns() }
    }
}
