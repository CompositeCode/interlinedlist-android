package com.interlinedlist.android.feature.documents.ui.browser

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.documents.data.DocumentsRepository
import com.interlinedlist.android.feature.documents.domain.Document
import com.interlinedlist.android.feature.documents.domain.FolderContents
import com.interlinedlist.android.feature.documents.domain.FolderNode
import com.interlinedlist.android.feature.documents.domain.FolderSummary
import com.interlinedlist.android.feature.documents.ui.common.isSubscriptionGate
import com.interlinedlist.android.feature.documents.ui.common.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Nav arg key the browser reads the current folder id from (absent == root). */
const val FOLDER_ID_ARG = "folderId"

/** Sentinel value the root route passes so nav treats "no folder" uniformly. */
const val ROOT_FOLDER_ARG = "__root__"

/** UI state for one level of the folder browser. */
data class DocumentsBrowserUiState(
    val contents: FolderContents = EMPTY_ROOT,
    val allFolders: List<FolderSummary> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val subscriptionRequired: Boolean = false,
    // Search overlay.
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val isSearching: Boolean = false,
    val searchResults: List<Document> = emptyList(),
) {
    val isEmpty: Boolean get() = contents.isEmpty && !isLoading

    companion object {
        val EMPTY_ROOT = FolderContents(
            folderId = FolderNode.ROOT_ID,
            folderName = FolderNode.ROOT_NAME,
            parentId = null,
            subfolders = emptyList(),
            documents = emptyList(),
            breadcrumb = emptyList(),
        )
    }
}

/**
 * Drives one drill-down level of the folder browser. The level's [FOLDER_ID_ARG]
 * (or the root when absent) selects which folder's contents to observe from the
 * offline-first repository. All folder/document management flows through here.
 */
@HiltViewModel
class DocumentsBrowserViewModel @Inject constructor(
    private val repository: DocumentsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** Null means the root ("Documents") level. */
    private val folderId: String? =
        savedStateHandle.get<String>(FOLDER_ID_ARG)?.takeUnless { it == ROOT_FOLDER_ARG }

    private val _uiState = MutableStateFlow(DocumentsBrowserUiState(isLoading = true))
    val uiState: StateFlow<DocumentsBrowserUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        observeContents()
        observeFolders()
        refresh()
    }

    private fun observeContents() {
        viewModelScope.launch {
            repository.observeFolderContents(folderId).collect { contents ->
                _uiState.update { it.copy(contents = contents) }
            }
        }
    }

    private fun observeFolders() {
        viewModelScope.launch {
            repository.observeFolderSummaries().collect { folders ->
                _uiState.update { it.copy(allFolders = folders) }
            }
        }
    }

    /** Pulls the whole tree from the API into Room; the observers render the result. */
    fun refresh() {
        _uiState.update {
            it.copy(isRefreshing = true, isLoading = it.contents.isEmpty, errorMessage = null)
        }
        viewModelScope.launch {
            when (val result = repository.refreshTree()) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, subscriptionRequired = false)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = result.error.toUserMessage(),
                        subscriptionRequired = result.error.isSubscriptionGate,
                    )
                }
            }
        }
    }

    // --- Document actions --------------------------------------------------

    /**
     * Creates a document in this folder; invokes [onCreated] with its id to open it.
     * Inside a folder this hits the dedicated "create in folder" endpoint (a single
     * call that files the doc directly); at the root it uses the plain create.
     */
    fun createDocument(title: String, onCreated: (String) -> Unit) {
        val trimmed = title.trim().ifBlank { "Untitled" }
        viewModelScope.launch {
            val result = if (folderId != null) {
                repository.createDocumentInFolder(
                    folderId = folderId,
                    title = trimmed,
                    content = "",
                    isPublic = false,
                )
            } else {
                repository.createDocument(
                    title = trimmed,
                    content = "",
                    isPublic = false,
                    folderId = null,
                )
            }
            when (result) {
                is ApiResult.Success -> onCreated(result.data.id)
                is ApiResult.Failure -> showError(result.error.toUserMessage())
            }
        }
    }

    /** Moves [documentId] into [targetFolderId] (null == root/unfiled). */
    fun moveDocument(documentId: String, targetFolderId: String?) {
        viewModelScope.launch {
            when (val result = repository.moveDocument(documentId, targetFolderId)) {
                is ApiResult.Success -> Unit // Observed contents update the UI.
                is ApiResult.Failure -> showError(result.error.toUserMessage())
            }
        }
    }

    fun deleteDocument(documentId: String) {
        viewModelScope.launch {
            when (val result = repository.deleteDocument(documentId)) {
                is ApiResult.Success -> Unit
                is ApiResult.Failure -> showError(result.error.toUserMessage())
            }
        }
    }

    // --- Folder actions ----------------------------------------------------

    fun createFolder(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            when (val result = repository.createFolder(trimmed, parentId = folderId)) {
                is ApiResult.Success -> Unit
                is ApiResult.Failure -> showError(result.error.toUserMessage())
            }
        }
    }

    fun renameFolder(id: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            when (val result = repository.renameFolder(id, trimmed)) {
                is ApiResult.Success -> Unit
                is ApiResult.Failure -> showError(result.error.toUserMessage())
            }
        }
    }

    fun deleteFolder(id: String) {
        viewModelScope.launch {
            when (val result = repository.deleteFolder(id)) {
                is ApiResult.Success -> Unit
                is ApiResult.Failure -> showError(result.error.toUserMessage())
            }
        }
    }

    // --- Search ------------------------------------------------------------

    fun openSearch() = _uiState.update { it.copy(isSearchActive = true) }

    fun closeSearch() = _uiState.update {
        it.copy(isSearchActive = false, searchQuery = "", searchResults = emptyList(), isSearching = false)
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        _uiState.update { it.copy(isSearching = true) }
        searchJob = viewModelScope.launch {
            when (val result = repository.searchDocuments(query.trim())) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isSearching = false, searchResults = result.data)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isSearching = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }

    private fun showError(message: String) = _uiState.update { it.copy(errorMessage = message) }
}
