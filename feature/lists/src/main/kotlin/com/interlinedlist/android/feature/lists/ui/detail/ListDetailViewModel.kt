package com.interlinedlist.android.feature.lists.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.lists.data.ListsRepository
import com.interlinedlist.android.feature.lists.domain.ListRow
import com.interlinedlist.android.feature.lists.domain.ListSchema
import com.interlinedlist.android.feature.lists.domain.ListSummary
import com.interlinedlist.android.feature.lists.ui.isSubscriptionGate
import com.interlinedlist.android.feature.lists.ui.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** UI state for the list detail screen (schema-driven table). */
data class ListDetailUiState(
    val summary: ListSummary? = null,
    val schema: ListSchema = ListSchema.EMPTY,
    val rows: List<ListRow> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val subscriptionRequired: Boolean = false,
    val isSaving: Boolean = false,
    val deleted: Boolean = false,
    val isRefreshing: Boolean = false,
    val refreshMessage: String? = null,
) {
    val title: String get() = summary?.title.orEmpty()
    val isEmpty: Boolean get() = rows.isEmpty() && !isLoading && errorMessage == null
}

/** The nav argument key the detail route reads its list id from. */
const val LIST_ID_ARG = "listId"

@HiltViewModel
class ListDetailViewModel @Inject constructor(
    private val repository: ListsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val listId: String = requireNotNull(savedStateHandle[LIST_ID_ARG]) {
        "ListDetailViewModel requires a '$LIST_ID_ARG' nav argument"
    }

    private val _uiState = MutableStateFlow(ListDetailUiState())
    val uiState: StateFlow<ListDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null, subscriptionRequired = false) }
        viewModelScope.launch {
            when (val result = repository.getListDetail(listId)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        summary = result.data.summary,
                        schema = result.data.schema,
                        rows = result.data.rows,
                        isLoading = false,
                    )
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = result.error.toUserMessage(),
                        subscriptionRequired = result.error.isSubscriptionGate,
                    )
                }
            }
        }
    }

    fun addRow(values: Map<String, String>, onDone: () -> Unit = {}) {
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            when (val result = repository.addRow(listId, values)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isSaving = false, rows = it.rows + result.data) }
                    onDone()
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isSaving = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    fun updateRow(rowId: String, values: Map<String, String>, onDone: () -> Unit = {}) {
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            when (val result = repository.updateRow(listId, rowId, values)) {
                is ApiResult.Success -> {
                    _uiState.update { state ->
                        state.copy(
                            isSaving = false,
                            rows = state.rows.map { if (it.id == rowId) result.data else it },
                        )
                    }
                    onDone()
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isSaving = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    fun deleteRow(rowId: String) {
        viewModelScope.launch {
            when (val result = repository.deleteRow(listId, rowId)) {
                is ApiResult.Success -> _uiState.update { state ->
                    state.copy(rows = state.rows.filterNot { it.id == rowId })
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    /**
     * Manually re-syncs a GitHub-backed list. On success the detail is reloaded so
     * the new rows appear, and a short summary is surfaced for the UI to toast.
     */
    fun refreshFromGithub() {
        if (_uiState.value.isRefreshing) return
        _uiState.update { it.copy(isRefreshing = true, errorMessage = null, refreshMessage = null) }
        viewModelScope.launch {
            when (val result = repository.refreshGithubList(listId)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            refreshMessage = result.data.summary
                                ?: result.data.message
                                ?: "List refreshed.",
                        )
                    }
                    // Pull the freshly-synced rows into view.
                    reload()
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isRefreshing = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    /** Reloads detail without toggling the top-level loading spinner (post-refresh). */
    private fun reload() {
        viewModelScope.launch {
            when (val result = repository.getListDetail(listId)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        summary = result.data.summary,
                        schema = result.data.schema,
                        rows = result.data.rows,
                    )
                }
                is ApiResult.Failure -> Unit // Keep the existing rows; refresh already succeeded.
            }
        }
    }

    fun clearRefreshMessage() = _uiState.update { it.copy(refreshMessage = null) }

    fun deleteList(onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            when (val result = repository.deleteList(listId)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(deleted = true) }
                    onDeleted()
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
}
