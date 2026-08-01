package com.interlinedlist.android.feature.documents.ui.share

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.documents.data.DocumentsRepository
import com.interlinedlist.android.feature.documents.domain.SharedDocument
import com.interlinedlist.android.feature.documents.ui.common.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The nav argument key the resolve route reads its share token from. */
const val SHARED_DOCUMENT_TOKEN_ARG = "token"

/** UI state for the resolve/claim screen of a shared document link. */
data class SharedDocumentUiState(
    val document: SharedDocument? = null,
    val isLoading: Boolean = true,
    val isClaiming: Boolean = false,
    val claimed: Boolean = false,
    val errorMessage: String? = null,
) {
    /** True when the resolved link grants edit/admin and hasn't been claimed yet. */
    val canClaim: Boolean get() = document?.canClaim == true && !claimed
}

/**
 * Resolves a `documents/shared/{token}` link to a read-only preview and, when the
 * link grants edit/admin, lets the visitor claim access under their own account.
 */
@HiltViewModel
class SharedDocumentViewModel @Inject constructor(
    private val repository: DocumentsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val token: String = requireNotNull(savedStateHandle[SHARED_DOCUMENT_TOKEN_ARG]) {
        "SharedDocumentViewModel requires a '$SHARED_DOCUMENT_TOKEN_ARG' nav argument"
    }

    private val _uiState = MutableStateFlow(SharedDocumentUiState())
    val uiState: StateFlow<SharedDocumentUiState> = _uiState.asStateFlow()

    init {
        resolve()
    }

    fun resolve() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.resolveSharedDocument(token)) {
                is ApiResult.Success -> _uiState.update { it.copy(document = result.data, isLoading = false) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    fun claim() {
        if (_uiState.value.document?.canClaim != true) return
        _uiState.update { it.copy(isClaiming = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.claimSharedDocument(token)) {
                is ApiResult.Success -> _uiState.update { it.copy(isClaiming = false, claimed = true) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isClaiming = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
}
