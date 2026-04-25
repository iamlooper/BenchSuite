package io.github.iamlooper.benchsuite.ui.screens.results

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.iamlooper.benchsuite.data.local.snapshotFlow
import io.github.iamlooper.benchsuite.data.local.updateSnapshot
import io.github.iamlooper.benchsuite.data.model.DeviceInfo
import io.github.iamlooper.benchsuite.data.model.RunResult
import io.github.iamlooper.benchsuite.data.repository.DeviceRepository
import io.github.iamlooper.benchsuite.data.repository.LeaderboardRepository
import io.github.iamlooper.benchsuite.data.repository.RunRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ResultsUiState {
    data object Loading : ResultsUiState
    data class Success(val run: RunResult, val device: DeviceInfo) : ResultsUiState
    data object Error : ResultsUiState
}

sealed interface UploadState {
    data object Idle : UploadState
    data object Uploading : UploadState
    data class Success(val supabaseRunId: String) : UploadState
    data class Failure(val message: String? = null) : UploadState
}

@HiltViewModel
class ResultsViewModel @Inject constructor(
    private val runRepository: RunRepository,
    private val leaderboardRepository: LeaderboardRepository,
    private val deviceRepository: DeviceRepository,
    private val dataStore: DataStore<Preferences>,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ResultsUiState>(ResultsUiState.Loading)
    val uiState: StateFlow<ResultsUiState> = _uiState.asStateFlow()

    val displayName: StateFlow<String> = dataStore.snapshotFlow()
        .map { it.displayName }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    fun saveDisplayName(name: String) {
        viewModelScope.launch {
            dataStore.updateSnapshot { it.copy(displayName = name.trim()) }
        }
    }

    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState: StateFlow<UploadState> = _uploadState.asStateFlow()
    private var requestedRunId: String? = null
    private var uploadRequestToken: Long = 0L

    /** Loads the run from Room by [runId]. */
    fun loadRun(runId: String) {
        requestedRunId = runId
        viewModelScope.launch {
            _uploadState.value = UploadState.Idle
            _uiState.value = ResultsUiState.Loading
            val nextState = try {
                val run    = runRepository.getRunById(runId)
                val device = deviceRepository.getDeviceInfo()
                if (run != null) {
                    ResultsUiState.Success(run, device)
                } else {
                    ResultsUiState.Error
                }
            } catch (_: Exception) {
                ResultsUiState.Error
            }
            if (requestedRunId == runId) {
                _uiState.value = nextState
            }
        }
    }

    /**
     * Uploads the run to Supabase via the Edge Function.
     *
     * @param displayName User's chosen leaderboard display name ("" → "Anonymous").
     */
    fun uploadRun(displayName: String) {
        val state = _uiState.value as? ResultsUiState.Success ?: return
        if (state.run.isUploaded || _uploadState.value == UploadState.Uploading) return
        val runId = state.run.id
        val requestToken = ++uploadRequestToken
        _uploadState.value = UploadState.Uploading
        viewModelScope.launch {
            val nextUploadState = try {
                val supabaseId = leaderboardRepository.uploadRun(
                    device      = state.device,
                    run         = state.run,
                    displayName = displayName,
                )
                UploadState.Success(supabaseId)
            } catch (e: Exception) {
                UploadState.Failure(message = e.message)
            }
            if (requestToken == uploadRequestToken && requestedRunId == runId) {
                if (nextUploadState is UploadState.Success) {
                    _uiState.value = ResultsUiState.Success(
                        run = state.run.copy(isUploaded = true),
                        device = state.device,
                    )
                }
                _uploadState.value = nextUploadState
            }
        }
    }
}
