package com.interlinedlist.android.feature.lists.ui.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.lists.data.ListsRepository
import com.interlinedlist.android.feature.lists.domain.ListFolder
import com.interlinedlist.android.feature.lists.ui.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** UI state for the folder browser: browse folders, plus rename/move/delete. */
data class FolderBrowserUiState(
    val folders: List<ListFolder> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
) {
    val isEmpty: Boolean get() = folders.isEmpty() && !isLoading && errorMessage == null
}

@HiltViewModel
class FolderBrowserViewModel @Inject constructor(
    private val repository: ListsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FolderBrowserUiState())
    val uiState: StateFlow<FolderBrowserUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.getFolders()) {
                is ApiResult.Success -> _uiState.update { it.copy(folders = result.data, isLoading = false) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    fun createFolder(name: String, parentId: String? = null) {
        if (name.isBlank()) return
        viewModelScope.launch {
            when (val result = repository.createFolder(name.trim(), parentId)) {
                is ApiResult.Success -> _uiState.update { it.copy(folders = it.folders + result.data) }
                is ApiResult.Failure -> _uiState.update { it.copy(errorMessage = result.error.toUserMessage()) }
            }
        }
    }

    /**
     * Renames a folder. The label is updated optimistically so the change shows
     * instantly; a failure rolls it back to the previous folders and surfaces the
     * error.
     */
    fun renameFolder(folder: ListFolder, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank() || trimmed == folder.name) return
        val previous = _uiState.value.folders
        _uiState.update { state ->
            state.copy(folders = state.folders.map { if (it.id == folder.id) it.copy(name = trimmed) else it })
        }
        viewModelScope.launch {
            when (val result = repository.updateFolder(folder.id, name = trimmed)) {
                is ApiResult.Success -> _uiState.update { state ->
                    state.copy(folders = state.folders.map { if (it.id == folder.id) result.data else it })
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(folders = previous, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    /**
     * Moves a folder under [newParentId] (null = root). Optimistic with rollback on
     * failure.
     */
    fun moveFolder(folder: ListFolder, newParentId: String?) {
        if (newParentId == folder.id) return // A folder cannot be its own parent.
        val previous = _uiState.value.folders
        _uiState.update { state ->
            state.copy(folders = state.folders.map { if (it.id == folder.id) it.copy(parentId = newParentId) else it })
        }
        viewModelScope.launch {
            when (val result = repository.updateFolder(folder.id, parentId = newParentId)) {
                is ApiResult.Success -> _uiState.update { state ->
                    state.copy(folders = state.folders.map { if (it.id == folder.id) result.data else it })
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(folders = previous, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    /**
     * Deletes a folder (confirmed by the UI). The row is removed optimistically and
     * restored on failure.
     */
    fun deleteFolder(folder: ListFolder) {
        val previous = _uiState.value.folders
        _uiState.update { state -> state.copy(folders = state.folders.filterNot { it.id == folder.id }) }
        viewModelScope.launch {
            when (val result = repository.deleteFolder(folder.id)) {
                is ApiResult.Success -> Unit
                is ApiResult.Failure -> _uiState.update {
                    it.copy(folders = previous, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
}
