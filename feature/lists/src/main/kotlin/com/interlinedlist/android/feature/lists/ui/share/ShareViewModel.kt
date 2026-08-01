package com.interlinedlist.android.feature.lists.ui.share

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.lists.data.ListsRepository
import com.interlinedlist.android.feature.lists.domain.ShareLink
import com.interlinedlist.android.feature.lists.domain.ShareRole
import com.interlinedlist.android.feature.lists.ui.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The nav argument key the share route reads its list id from. */
const val SHARE_LIST_ID_ARG = "listId"

/** UI state for the list share sheet. */
data class ShareUiState(
    val links: List<ShareLink> = emptyList(),
    val selectedRole: ShareRole = ShareRole.VIEW,
    val isLoading: Boolean = true,
    val isCreating: Boolean = false,
    val errorMessage: String? = null,
) {
    /** Only active (non-revoked) links are shown as usable. */
    val activeLinks: List<ShareLink> get() = links.filter { it.isActive }
    val isEmpty: Boolean get() = activeLinks.isEmpty() && !isLoading && errorMessage == null
}

/**
 * Drives the list share sheet: load existing links, create a link for a chosen role
 * (optimistically appended, rolled back on failure), and revoke a link (optimistically
 * removed, rolled back on failure). Reads its `listId` from the nav SavedStateHandle.
 */
@HiltViewModel
class ShareViewModel @Inject constructor(
    private val repository: ListsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val listId: String = requireNotNull(savedStateHandle[SHARE_LIST_ID_ARG]) {
        "ShareViewModel requires a '$SHARE_LIST_ID_ARG' nav argument"
    }

    private val _uiState = MutableStateFlow(ShareUiState())
    val uiState: StateFlow<ShareUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.getShareLinks(listId)) {
                is ApiResult.Success -> _uiState.update { it.copy(links = result.data, isLoading = false) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    fun selectRole(role: ShareRole) = _uiState.update { it.copy(selectedRole = role) }

    fun createLink() {
        val role = _uiState.value.selectedRole
        _uiState.update { it.copy(isCreating = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.createShareLink(listId, role)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(links = it.links + result.data, isCreating = false)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isCreating = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    /** Optimistically removes the link; restores it (and shows an error) on failure. */
    fun revokeLink(link: ShareLink) {
        val previous = _uiState.value.links
        _uiState.update { it.copy(links = it.links.filterNot { existing -> existing.token == link.token }) }
        viewModelScope.launch {
            when (val result = repository.revokeShareLink(listId, link.token)) {
                is ApiResult.Success -> Unit
                is ApiResult.Failure -> _uiState.update {
                    it.copy(links = previous, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
}
