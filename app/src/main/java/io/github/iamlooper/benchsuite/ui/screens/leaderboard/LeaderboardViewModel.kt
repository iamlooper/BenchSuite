package io.github.iamlooper.benchsuite.ui.screens.leaderboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.iamlooper.benchsuite.BuildConfig
import io.github.iamlooper.benchsuite.data.model.LeaderboardEntry
import io.github.iamlooper.benchsuite.data.repository.DeviceRepository
import io.github.iamlooper.benchsuite.data.repository.LeaderboardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LeaderboardUiState {
    data object Loading : LeaderboardUiState
    data class Success(
        val entries: List<LeaderboardEntry>,
        val hasMore: Boolean = false,
        val isLoadingMore: Boolean = false,
        val selectedAppVersion: String? = null,
        val availableAppVersions: List<String> = emptyList(),
        val myRunsOnly: Boolean = false,
        // The device's own installed version; used by the screen to decide
        // whether to show the cross-version comparison warning.
        val userAppVersion: String,
    ) : LeaderboardUiState
    data object Error : LeaderboardUiState
}

@HiltViewModel
class LeaderboardViewModel @Inject constructor(
    private val leaderboardRepository: LeaderboardRepository,
    deviceRepository: DeviceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LeaderboardUiState>(LeaderboardUiState.Loading)
    val uiState: StateFlow<LeaderboardUiState> = _uiState.asStateFlow()
    private var isRefreshing = false
    private var currentOffset = 0
    // Default to the user's own installed version so scores are comparable on first open.
    private var selectedAppVersion: String? = BuildConfig.VERSION_NAME
    private var myRunsOnly = false

    // Computed once at construction; DeviceRepository.getDeviceInfo() is a pure synchronous call.
    private val deviceFingerprintHash: String = deviceRepository.getDeviceInfo().fingerprintHash

    companion object {
        private const val PAGE_SIZE = 50
    }

    init { refresh() }

    fun refresh() {
        if (isRefreshing) return
        isRefreshing = true
        currentOffset = 0
        _uiState.value = LeaderboardUiState.Loading
        viewModelScope.launch {
            try {
                val fingerprintHash = if (myRunsOnly) deviceFingerprintHash else null
                val entries = leaderboardRepository.fetchLeaderboard(
                    appVersion      = selectedAppVersion,
                    fingerprintHash = fingerprintHash,
                    offset          = 0,
                    limit           = PAGE_SIZE,
                )
                // Always refresh so the list is current after an upload.
                val rawVersions = leaderboardRepository.refreshAppVersions()
                // Guarantee the installed version is always selectable even before
                // any run for it has been uploaded.
                val versions = if (BuildConfig.VERSION_NAME in rawVersions) rawVersions
                    else listOf(BuildConfig.VERSION_NAME) + rawVersions
                currentOffset = entries.size
                _uiState.value = LeaderboardUiState.Success(
                    entries              = entries,
                    hasMore              = entries.size >= PAGE_SIZE,
                    selectedAppVersion   = selectedAppVersion,
                    availableAppVersions = versions,
                    myRunsOnly           = myRunsOnly,
                    userAppVersion       = BuildConfig.VERSION_NAME,
                )
            } catch (_: Exception) {
                _uiState.value = LeaderboardUiState.Error
            } finally {
                isRefreshing = false
            }
        }
    }

    fun loadMore() {
        val current = _uiState.value as? LeaderboardUiState.Success ?: return
        if (current.isLoadingMore || !current.hasMore) return
        _uiState.value = current.copy(isLoadingMore = true)
        viewModelScope.launch {
            try {
                val fingerprintHash = if (myRunsOnly) deviceFingerprintHash else null
                val more = leaderboardRepository.fetchLeaderboard(
                    appVersion      = selectedAppVersion,
                    fingerprintHash = fingerprintHash,
                    offset          = currentOffset,
                    limit           = PAGE_SIZE,
                )
                currentOffset += more.size
                _uiState.value = current.copy(
                    entries       = current.entries + more,
                    hasMore       = more.size >= PAGE_SIZE,
                    isLoadingMore = false,
                )
            } catch (_: Exception) {
                _uiState.value = current.copy(isLoadingMore = false)
            }
        }
    }

    fun setAppVersionFilter(version: String?) {
        if (selectedAppVersion == version) return
        selectedAppVersion = version
        refresh()
    }

    fun toggleMyRunsFilter() {
        myRunsOnly = !myRunsOnly
        refresh()
    }
}
