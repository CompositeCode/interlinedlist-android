package com.interlinedlist.android.feature.documents.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.documents.data.DocumentsRepository
import com.interlinedlist.android.feature.documents.data.SaveOutcome
import com.interlinedlist.android.feature.documents.ui.common.isSubscriptionGate
import com.interlinedlist.android.feature.documents.ui.common.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** UI state for the document detail / editor. */
data class DocumentEditorUiState(
    val documentId: String = "",
    val title: String = "",
    val content: String = "",
    val isPublic: Boolean = false,
    val folderId: String? = null,
    /** Server version token used for optimistic-concurrency on save (PATCH If-Match). */
    val version: Int? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isUploadingImage: Boolean = false,
    val isPreview: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    /** True when the last save was rejected because another writer changed the doc. */
    val hasConflict: Boolean = false,
    /** True when the last save could not reach the server and was queued to sync later. */
    val isQueuedOffline: Boolean = false,
    val errorMessage: String? = null,
    val subscriptionRequired: Boolean = false,
) {
    val canSave: Boolean get() = hasUnsavedChanges && !isSaving && !isLoading
}

/** Nav arg key the editor reads its target document id from. */
const val DOCUMENT_ID_ARG = "documentId"

@HiltViewModel
class DocumentEditorViewModel @Inject constructor(
    private val repository: DocumentsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val documentId: String = checkNotNull(savedStateHandle[DOCUMENT_ID_ARG]) {
        "DocumentEditorViewModel requires a '$DOCUMENT_ID_ARG' nav arg"
    }

    private val _uiState = MutableStateFlow(DocumentEditorUiState(documentId = documentId))
    val uiState: StateFlow<DocumentEditorUiState> = _uiState.asStateFlow()

    init {
        observeCached()
        refresh()
    }

    /** Seeds the editor from the Room cache so it renders instantly offline. */
    private fun observeCached() {
        viewModelScope.launch {
            repository.observeDocument(documentId).collect { cached ->
                if (cached != null && !_uiState.value.hasUnsavedChanges) {
                    _uiState.update {
                        it.copy(
                            title = cached.title,
                            content = cached.content ?: it.content,
                            isPublic = cached.isPublic,
                            folderId = cached.folderId,
                            version = cached.version ?: it.version,
                        )
                    }
                }
            }
        }
    }

    /** Fetches the full document (with body) from the API. */
    fun refresh(discardLocalEdits: Boolean = false) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.refreshDocument(documentId)) {
                is ApiResult.Success -> _uiState.update {
                    // Don't clobber in-progress edits with the server copy, unless the
                    // caller explicitly resolves a conflict by discarding them.
                    if (it.hasUnsavedChanges && !discardLocalEdits) {
                        it.copy(isLoading = false, version = result.data.version ?: it.version)
                    } else {
                        it.copy(
                            title = result.data.title,
                            content = result.data.content ?: "",
                            isPublic = result.data.isPublic,
                            folderId = result.data.folderId,
                            version = result.data.version ?: it.version,
                            isLoading = false,
                            hasUnsavedChanges = false,
                            hasConflict = false,
                            isQueuedOffline = false,
                            subscriptionRequired = false,
                        )
                    }
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

    /** Resolves a save conflict by reloading the latest server copy, discarding local edits. */
    fun reloadForConflict() = refresh(discardLocalEdits = true)

    fun onTitleChange(value: String) =
        _uiState.update {
            it.copy(title = value, hasUnsavedChanges = true, hasConflict = false, isQueuedOffline = false, errorMessage = null)
        }

    fun onContentChange(value: String) =
        _uiState.update {
            it.copy(content = value, hasUnsavedChanges = true, hasConflict = false, isQueuedOffline = false, errorMessage = null)
        }

    fun togglePreview() = _uiState.update { it.copy(isPreview = !it.isPreview) }

    /**
     * Uploads a picked image for this document and appends a markdown image
     * reference for it into the body. The screen supplies the raw bytes read from
     * the picker's content URI; keeping the VM byte-based avoids an Android
     * dependency here and keeps it unit-testable.
     */
    fun uploadImage(fileName: String, mimeType: String, bytes: ByteArray) {
        _uiState.update { it.copy(isUploadingImage = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.uploadImage(documentId, fileName, mimeType, bytes)) {
                is ApiResult.Success -> _uiState.update {
                    val marker = "\n![${fileName}](uploading…)\n"
                    it.copy(
                        isUploadingImage = false,
                        content = it.content + marker,
                        hasUnsavedChanges = true,
                    )
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isUploadingImage = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    /**
     * Persists edits via `PATCH` with the current version token so a concurrent
     * writer's change is detected rather than clobbered. On success (or an offline
     * queue) invokes [onSaved]; on a version conflict raises [DocumentEditorUiState.hasConflict]
     * so the screen can offer reload/retry.
     */
    fun save(onSaved: () -> Unit = {}) {
        val state = _uiState.value
        if (!state.canSave) return
        _uiState.update { it.copy(isSaving = true, errorMessage = null, hasConflict = false, isQueuedOffline = false) }
        viewModelScope.launch {
            val outcome = repository.patchDocument(
                id = documentId,
                title = state.title.trim().ifBlank { "Untitled" },
                content = state.content,
                isPublic = state.isPublic,
                folderId = state.folderId,
                expectedVersion = state.version,
            )
            when (outcome) {
                is SaveOutcome.Success -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            hasUnsavedChanges = false,
                            version = outcome.document.version ?: it.version,
                        )
                    }
                    onSaved()
                }
                is SaveOutcome.Queued -> {
                    // Persisted locally; the sync worker will push it. Treat as saved for UX.
                    _uiState.update {
                        it.copy(isSaving = false, hasUnsavedChanges = false, isQueuedOffline = true)
                    }
                    onSaved()
                }
                is SaveOutcome.Conflict -> _uiState.update {
                    it.copy(
                        isSaving = false,
                        hasConflict = true,
                        errorMessage = outcome.message
                            ?: "This document changed since you opened it. Reload to see the latest, or retry to overwrite.",
                    )
                }
                is SaveOutcome.Error -> _uiState.update {
                    it.copy(isSaving = false, errorMessage = outcome.message ?: "Couldn't save. Please try again.")
                }
            }
        }
    }

    /** Deletes the document; invokes [onDeleted] on success. */
    fun delete(onDeleted: () -> Unit) {
        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.deleteDocument(documentId)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isSaving = false) }
                    onDeleted()
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isSaving = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
}
