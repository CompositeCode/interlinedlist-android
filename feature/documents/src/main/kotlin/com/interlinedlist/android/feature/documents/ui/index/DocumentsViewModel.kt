package com.interlinedlist.android.feature.documents.ui.index

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.documents.data.DocumentsRepository
import com.interlinedlist.android.feature.documents.domain.Document
import com.interlinedlist.android.feature.documents.domain.DocumentFolder
import com.interlinedlist.android.feature.documents.domain.DocumentTemplate
import com.interlinedlist.android.feature.documents.domain.Pagination
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

/** UI state for the documents index. */
data class DocumentsUiState(
    val documents: List<Document> = emptyList(),
    val folders: List<DocumentFolder> = emptyList(),
    val selectedFolderId: String? = null,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val hasMore: Boolean = false,
    val errorMessage: String? = null,
    val subscriptionRequired: Boolean = false,
    // Template picker state.
    val templates: List<DocumentTemplate> = emptyList(),
    val isLoadingTemplates: Boolean = false,
) {
    val isEmpty: Boolean get() = documents.isEmpty() && !isLoading
}

@HiltViewModel
class DocumentsViewModel @Inject constructor(
    private val repository: DocumentsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DocumentsUiState())
    val uiState: StateFlow<DocumentsUiState> = _uiState.asStateFlow()

    /** Paging cursor for the current folder scope; advanced by [loadMore]. */
    private var pagination: Pagination = Pagination.single(0)
    private var observeJob: Job? = null

    init {
        observeDocuments(folderId = null)
        observeFolders()
        refresh()
    }

    /** Re-points the Room observer at the given scope (root when null). */
    private fun observeDocuments(folderId: String?) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            repository.observeDocuments(folderId).collect { docs ->
                _uiState.update { it.copy(documents = docs) }
            }
        }
    }

    private fun observeFolders() {
        viewModelScope.launch {
            repository.observeFolders().collect { folders ->
                _uiState.update { it.copy(folders = folders) }
            }
        }
    }

    /** Switches the visible folder and refreshes it from the API. */
    fun selectFolder(folderId: String?) {
        if (folderId == _uiState.value.selectedFolderId) return
        _uiState.update { it.copy(selectedFolderId = folderId, documents = emptyList()) }
        observeDocuments(folderId)
        refresh()
    }

    /** Pulls the first page for the current scope from the API. */
    fun refresh() {
        val folderId = _uiState.value.selectedFolderId
        _uiState.update { it.copy(isRefreshing = true, isLoading = it.documents.isEmpty(), errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.refreshDocuments(folderId)) {
                is ApiResult.Success -> {
                    pagination = result.data
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            hasMore = result.data.hasMore,
                            subscriptionRequired = false,
                        )
                    }
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
            refreshFolders()
        }
    }

    private suspend fun refreshFolders() {
        // Best-effort; folder failures don't block the document list.
        repository.refreshFolders()
    }

    /** Appends the next page when [DocumentsUiState.hasMore]. */
    fun loadMore() {
        val state = _uiState.value
        if (!state.hasMore || state.isLoadingMore) return
        _uiState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            when (val result = repository.loadMore(state.selectedFolderId, pagination)) {
                is ApiResult.Success -> {
                    pagination = result.data
                    _uiState.update { it.copy(isLoadingMore = false, hasMore = result.data.hasMore) }
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isLoadingMore = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    /** Creates a document in the current scope; invokes [onCreated] with its id. */
    fun createDocument(title: String, onCreated: (String) -> Unit) {
        val trimmed = title.trim().ifBlank { "Untitled" }
        viewModelScope.launch {
            when (val result = repository.createDocument(trimmed, content = "", isPublic = false)) {
                is ApiResult.Success -> onCreated(result.data.id)
                is ApiResult.Failure -> _uiState.update {
                    it.copy(errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    /** Loads templates for the picker (lazily, when the sheet opens). */
    fun loadTemplates() {
        _uiState.update { it.copy(isLoadingTemplates = true) }
        viewModelScope.launch {
            when (val result = repository.getTemplates()) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isLoadingTemplates = false, templates = result.data)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isLoadingTemplates = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    fun createFromTemplate(templateId: String, onCreated: (String) -> Unit) {
        val targetFolderId = _uiState.value.selectedFolderId
        viewModelScope.launch {
            when (val result = repository.createFromTemplate(templateId, targetFolderId)) {
                is ApiResult.Success -> onCreated(result.data.id)
                is ApiResult.Failure -> _uiState.update {
                    it.copy(errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    fun createFolder(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            when (val result = repository.createFolder(trimmed, parentId = null)) {
                is ApiResult.Success -> Unit // Observed folders flow updates the UI.
                is ApiResult.Failure -> _uiState.update {
                    it.copy(errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
}
