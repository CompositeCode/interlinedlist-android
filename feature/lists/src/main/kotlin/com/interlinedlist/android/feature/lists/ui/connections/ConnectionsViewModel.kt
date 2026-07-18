package com.interlinedlist.android.feature.lists.ui.connections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.lists.data.ListsRepository
import com.interlinedlist.android.feature.lists.domain.ListConnection
import com.interlinedlist.android.feature.lists.domain.ListSummary
import com.interlinedlist.android.feature.lists.ui.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** UI state for the connections screen. */
data class ConnectionsUiState(
    val connections: List<ListConnection> = emptyList(),
    val lists: List<ListSummary> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
) {
    val isEmpty: Boolean get() = connections.isEmpty() && !isLoading && errorMessage == null

    /** Lists selectable as connection endpoints; needs at least two to connect. */
    val canCreate: Boolean get() = lists.size >= 2 && !isSaving
}

@HiltViewModel
class ConnectionsViewModel @Inject constructor(
    private val repository: ListsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectionsUiState())
    val uiState: StateFlow<ConnectionsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.getConnections()) {
                is ApiResult.Success -> _uiState.update { it.copy(connections = result.data, isLoading = false) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.error.toUserMessage())
                }
            }
            // The picker needs the user's lists as endpoints; a failure just leaves it empty.
            when (val lists = repository.refreshLists()) {
                is ApiResult.Success -> _uiState.update { it.copy(lists = lists.data.items) }
                is ApiResult.Failure -> Unit
            }
        }
    }

    fun createConnection(fromListId: String, toListId: String, label: String?, onDone: () -> Unit = {}) {
        if (fromListId.isBlank() || toListId.isBlank() || fromListId == toListId) return
        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.createConnection(fromListId, toListId, label)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isSaving = false, connections = it.connections + result.data) }
                    onDone()
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isSaving = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    fun deleteConnection(id: String) {
        viewModelScope.launch {
            when (val result = repository.deleteConnection(id)) {
                is ApiResult.Success -> _uiState.update { state ->
                    state.copy(connections = state.connections.filterNot { it.id == id })
                }
                is ApiResult.Failure -> _uiState.update { it.copy(errorMessage = result.error.toUserMessage()) }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
}
