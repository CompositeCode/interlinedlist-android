package com.interlinedlist.android.feature.profile.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.profile.data.ProfileRepository
import com.interlinedlist.android.feature.profile.domain.LoginSession
import com.interlinedlist.android.feature.profile.ui.common.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** UI state for the Active Sessions screen. */
data class SessionsUiState(
    val sessions: List<LoginSession> = emptyList(),
    val isLoading: Boolean = true,
    // Ids currently being revoked, so their row can show progress and dedupe taps.
    val pendingRevokeIds: Set<String> = emptySet(),
    val errorMessage: String? = null,
) {
    /** A load finished with no sessions and no error. */
    val isEmpty: Boolean get() = sessions.isEmpty() && !isLoading && errorMessage == null
}

/**
 * Drives the Active Sessions screen. Loads the current user's login sessions via
 * `GET /api/user/sessions` and revokes a non-current session via
 * `DELETE /api/user/sessions/{id}`, removing the row optimistically and rolling it
 * back if the revoke fails.
 */
@HiltViewModel
class SessionsViewModel @Inject constructor(
    private val repository: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionsUiState())
    val uiState: StateFlow<SessionsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.getSessions()) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(sessions = result.data, isLoading = false)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    /**
     * Revokes (signs out) the session [sessionId], removing its row optimistically.
     * The current device cannot be revoked here, and an in-flight revoke is deduped.
     */
    fun revoke(sessionId: String) {
        val state = _uiState.value
        val target = state.sessions.firstOrNull { it.id == sessionId } ?: return
        if (target.isCurrent || sessionId in state.pendingRevokeIds) return

        // Optimistically drop the row while marking it in-flight for rollback.
        _uiState.update {
            it.copy(
                sessions = it.sessions.filterNot { s -> s.id == sessionId },
                pendingRevokeIds = it.pendingRevokeIds + sessionId,
                errorMessage = null,
            )
        }
        viewModelScope.launch {
            when (val result = repository.revokeSession(sessionId)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(pendingRevokeIds = it.pendingRevokeIds - sessionId)
                }
                is ApiResult.Failure -> _uiState.update {
                    // Roll the removed row back into place and surface the error.
                    it.copy(
                        sessions = (it.sessions + target).sortedByDescending { s -> s.isCurrent },
                        pendingRevokeIds = it.pendingRevokeIds - sessionId,
                        errorMessage = result.error.toUserMessage(),
                    )
                }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
}
