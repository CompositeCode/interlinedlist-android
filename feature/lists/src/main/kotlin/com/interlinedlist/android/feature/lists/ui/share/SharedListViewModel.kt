package com.interlinedlist.android.feature.lists.ui.share

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.lists.data.ListsRepository
import com.interlinedlist.android.feature.lists.domain.SharedListResolution
import com.interlinedlist.android.feature.lists.ui.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The nav argument key the resolve route reads its share token from. */
const val SHARED_TOKEN_ARG = "token"

/** UI state for the resolve/claim screen of a shared list link. */
data class SharedListUiState(
    val resolution: SharedListResolution? = null,
    val isLoading: Boolean = true,
    val isClaiming: Boolean = false,
    val claimed: Boolean = false,
    val errorMessage: String? = null,
) {
    /** True when the resolved link grants edit/admin and hasn't been claimed yet. */
    val canClaim: Boolean get() = resolution?.canClaim == true && !claimed
}

/**
 * Resolves a `…/shared/{token}` link to a read-only preview and, when the link
 * grants edit/admin, lets the visitor claim access under their own account. Reads
 * the token from the nav SavedStateHandle.
 */
@HiltViewModel
class SharedListViewModel @Inject constructor(
    private val repository: ListsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val token: String = requireNotNull(savedStateHandle[SHARED_TOKEN_ARG]) {
        "SharedListViewModel requires a '$SHARED_TOKEN_ARG' nav argument"
    }

    private val _uiState = MutableStateFlow(SharedListUiState())
    val uiState: StateFlow<SharedListUiState> = _uiState.asStateFlow()

    init {
        resolve()
    }

    fun resolve() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.resolveSharedList(token)) {
                is ApiResult.Success -> _uiState.update { it.copy(resolution = result.data, isLoading = false) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    fun claim() {
        if (_uiState.value.resolution?.canClaim != true) return
        _uiState.update { it.copy(isClaiming = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.claimSharedList(token)) {
                is ApiResult.Success -> _uiState.update { it.copy(isClaiming = false, claimed = true) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isClaiming = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
}
