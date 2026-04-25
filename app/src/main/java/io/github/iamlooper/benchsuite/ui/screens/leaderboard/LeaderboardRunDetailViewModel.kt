package io.github.iamlooper.benchsuite.ui.screens.leaderboard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.iamlooper.benchsuite.data.model.LeaderboardEntry
import io.github.iamlooper.benchsuite.data.repository.LeaderboardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LeaderboardRunDetailUiState {
    data object Loading : LeaderboardRunDetailUiState
    data class Success(val entry: LeaderboardEntry) : LeaderboardRunDetailUiState
    data object Error : LeaderboardRunDetailUiState
}

@HiltViewModel
class LeaderboardRunDetailViewModel @Inject constructor(
    private val leaderboardRepository: LeaderboardRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val runId: String = savedStateHandle["runId"] ?: ""

    private val _uiState = MutableStateFlow<LeaderboardRunDetailUiState>(LeaderboardRunDetailUiState.Loading)
    val uiState: StateFlow<LeaderboardRunDetailUiState> = _uiState.asStateFlow()

    init {
        loadEntry()
    }

    private fun loadEntry() {
        viewModelScope.launch {
            // Use the cache if it already includes benchmark details to avoid a network round-trip.
            val cached = leaderboardRepository.getCachedEntry(runId)
            if (cached != null && cached.categories.isNotEmpty()) {
                _uiState.value = LeaderboardRunDetailUiState.Success(cached)
                return@launch
            }
            // Cache miss or cached entry pre-dates benchmark detail support, fetch from server.
            val rank = cached?.rank ?: 0
            val fetched = leaderboardRepository.fetchRunDetail(runId, rank)
            _uiState.value = when {
                fetched != null -> LeaderboardRunDetailUiState.Success(fetched)
                // Fall back to cached entry without benchmark details rather than showing an error.
                cached != null  -> LeaderboardRunDetailUiState.Success(cached)
                else            -> LeaderboardRunDetailUiState.Error
            }
        }
    }
}
