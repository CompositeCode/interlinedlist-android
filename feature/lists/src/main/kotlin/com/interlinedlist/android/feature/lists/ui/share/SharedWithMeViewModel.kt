package com.interlinedlist.android.feature.lists.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.lists.data.ListsRepository
import com.interlinedlist.android.feature.lists.domain.SharedList
import com.interlinedlist.android.feature.lists.ui.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** UI state for the "Shared with me" section. */
data class SharedWithMeUiState(
    val lists: List<SharedList> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
) {
    val isEmpty: Boolean get() = lists.isEmpty() && !isLoading && errorMessage == null
}

/** Loads lists shared with the current user (`GET /api/lists/watching`). */
@HiltViewModel
class SharedWithMeViewModel @Inject constructor(
    private val repository: ListsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SharedWithMeUiState())
    val uiState: StateFlow<SharedWithMeUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.getSharedWithMe()) {
                is ApiResult.Success -> _uiState.update { it.copy(lists = result.data, isLoading = false) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }
}
