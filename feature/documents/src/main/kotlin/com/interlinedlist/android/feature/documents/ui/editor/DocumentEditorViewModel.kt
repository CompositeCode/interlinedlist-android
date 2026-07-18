package com.interlinedlist.android.feature.documents.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.documents.data.DocumentsRepository
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
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isUploadingImage: Boolean = false,
    val isPreview: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
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
                        )
                    }
                }
            }
        }
    }

    /** Fetches the full document (with body) from the API. */
    fun refresh() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.refreshDocument(documentId)) {
                is ApiResult.Success -> _uiState.update {
                    // Don't clobber in-progress edits with the server copy.
                    if (it.hasUnsavedChanges) {
                        it.copy(isLoading = false)
                    } else {
                        it.copy(
                            title = result.data.title,
                            content = result.data.content ?: "",
                            isPublic = result.data.isPublic,
                            folderId = result.data.folderId,
                            isLoading = false,
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

    fun onTitleChange(value: String) =
        _uiState.update { it.copy(title = value, hasUnsavedChanges = true, errorMessage = null) }

    fun onContentChange(value: String) =
        _uiState.update { it.copy(content = value, hasUnsavedChanges = true, errorMessage = null) }

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

    /** Persists edits; invokes [onSaved] on success. */
    fun save(onSaved: () -> Unit = {}) {
        val state = _uiState.value
        if (!state.canSave) return
        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            val result = repository.updateDocument(
                id = documentId,
                title = state.title.trim().ifBlank { "Untitled" },
                content = state.content,
                isPublic = state.isPublic,
                folderId = state.folderId,
            )
            when (result) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isSaving = false, hasUnsavedChanges = false) }
                    onSaved()
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isSaving = false, errorMessage = result.error.toUserMessage())
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
