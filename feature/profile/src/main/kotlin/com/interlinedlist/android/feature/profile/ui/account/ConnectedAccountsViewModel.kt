package com.interlinedlist.android.feature.profile.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.profile.data.ProfileRepository
import com.interlinedlist.android.feature.profile.domain.LinkedIdentity
import com.interlinedlist.android.feature.profile.ui.common.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** UI state for the Connected Accounts screen. */
data class ConnectedAccountsUiState(
    val identities: List<LinkedIdentity> = emptyList(),
    val isLoading: Boolean = true,
    // Ids currently being unlinked, so their row can show progress and dedupe taps.
    val pendingUnlinkIds: Set<String> = emptySet(),
    val errorMessage: String? = null,
) {
    /** A load finished with no identities and no error. */
    val isEmpty: Boolean get() = identities.isEmpty() && !isLoading && errorMessage == null
}

/**
 * Drives the Connected Accounts screen. Loads linked identities via
 * `GET /api/user/identities` and unlinks one via
 * `DELETE /api/user/identities?provider=...`, removing the row optimistically and
 * rolling it back if the unlink fails.
 */
@HiltViewModel
class ConnectedAccountsViewModel @Inject constructor(
    private val repository: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectedAccountsUiState())
    val uiState: StateFlow<ConnectedAccountsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.getIdentities()) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(identities = result.data, isLoading = false)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    /**
     * Unlinks the identity [identityId] (keyed on its provider for the API), removing
     * its row optimistically. An in-flight unlink is deduped.
     */
    fun unlink(identityId: String) {
        val state = _uiState.value
        val target = state.identities.firstOrNull { it.id == identityId } ?: return
        if (identityId in state.pendingUnlinkIds) return

        _uiState.update {
            it.copy(
                identities = it.identities.filterNot { i -> i.id == identityId },
                pendingUnlinkIds = it.pendingUnlinkIds + identityId,
                errorMessage = null,
            )
        }
        viewModelScope.launch {
            when (val result = repository.unlinkIdentity(target.provider)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(pendingUnlinkIds = it.pendingUnlinkIds - identityId)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(
                        identities = it.identities + target,
                        pendingUnlinkIds = it.pendingUnlinkIds - identityId,
                        errorMessage = result.error.toUserMessage(),
                    )
                }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
}
