package io.github.iamlooper.benchsuite.ui.screens.leaderboard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.iamlooper.benchsuite.data.model.Category
import io.github.iamlooper.benchsuite.data.model.CategoryScore
import io.github.iamlooper.benchsuite.data.repository.LeaderboardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LeaderboardCategoryDetailUiState {
    data object Loading : LeaderboardCategoryDetailUiState
    data class Success(val categoryScore: CategoryScore) : LeaderboardCategoryDetailUiState
    data object Error : LeaderboardCategoryDetailUiState
}

@HiltViewModel
class LeaderboardCategoryDetailViewModel @Inject constructor(
    private val leaderboardRepository: LeaderboardRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val runId: String = savedStateHandle["runId"] ?: ""
    private val categoryId: String = savedStateHandle["categoryId"] ?: ""

    private val _uiState = MutableStateFlow<LeaderboardCategoryDetailUiState>(LeaderboardCategoryDetailUiState.Loading)
    val uiState: StateFlow<LeaderboardCategoryDetailUiState> = _uiState.asStateFlow()

    init {
        loadCategory()
    }

    private fun loadCategory() {
        val requestedCategory = Category.fromStringOrNull(categoryId)
        if (requestedCategory == null) {
            _uiState.value = LeaderboardCategoryDetailUiState.Error
            return
        }

        viewModelScope.launch {
            val cached = leaderboardRepository.getCachedEntry(runId)
            val entry = if (cached?.categories?.isNotEmpty() == true) {
                cached
            } else {
                leaderboardRepository.fetchRunDetail(runId, cached?.rank ?: 0) ?: cached
            }

            val categoryScore = entry?.categories?.firstOrNull { it.category == requestedCategory }
            _uiState.value = if (categoryScore != null) {
                LeaderboardCategoryDetailUiState.Success(categoryScore)
            } else {
                LeaderboardCategoryDetailUiState.Error
            }
        }
    }
}
