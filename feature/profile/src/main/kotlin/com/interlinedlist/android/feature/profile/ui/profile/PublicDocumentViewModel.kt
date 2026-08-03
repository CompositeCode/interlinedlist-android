package com.interlinedlist.android.feature.profile.ui.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.profile.data.ProfileRepository
import com.interlinedlist.android.feature.profile.domain.PublicDocumentDetail
import com.interlinedlist.android.feature.profile.ui.common.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Nav arg key the public-document route reads its document id from. */
const val PUBLIC_DOCUMENT_ID_ARG = "documentId"

/** UI state for the read-only public document view. */
data class PublicDocumentUiState(
    val document: PublicDocumentDetail? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

/**
 * Drives a read-only view of a public document (route `publicDocument/{documentId}`).
 * Loads the document (title + content) from `GET /api/documents/{id}`. No caching (YAGNI).
 */
@HiltViewModel
class PublicDocumentViewModel @Inject constructor(
    private val repository: ProfileRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val documentId: String = checkNotNull(savedStateHandle[PUBLIC_DOCUMENT_ID_ARG]) {
        "PublicDocumentViewModel requires a '$PUBLIC_DOCUMENT_ID_ARG' nav arg"
    }

    private val _uiState = MutableStateFlow(PublicDocumentUiState())
    val uiState: StateFlow<PublicDocumentUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.getDocument(documentId)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(document = result.data, isLoading = false)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }
}
