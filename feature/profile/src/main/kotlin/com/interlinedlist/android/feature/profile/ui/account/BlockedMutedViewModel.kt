package com.interlinedlist.android.feature.profile.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.profile.data.ProfileRepository
import com.interlinedlist.android.feature.profile.domain.ModeratedUser
import com.interlinedlist.android.feature.profile.ui.common.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** UI state for the "Blocked & muted" management screen. */
data class BlockedMutedUiState(
    val blocked: List<ModeratedUser> = emptyList(),
    val muted: List<ModeratedUser> = emptyList(),
    val isLoading: Boolean = true,
    // Usernames with an un-block / un-mute in flight, so their row can show progress
    // and repeat taps are deduped.
    val pendingIds: Set<String> = emptySet(),
    val errorMessage: String? = null,
) {
    /** A load finished with nothing on either list and no error. */
    val isEmpty: Boolean
        get() = blocked.isEmpty() && muted.isEmpty() && !isLoading && errorMessage == null
}

/**
 * Drives the "Blocked & muted" management screen (route `account/blocked-muted`). Loads
 * both lists via `GET /api/user/blocks` and `GET /api/user/mutes`, and un-blocks /
 * un-mutes by username, removing the row optimistically and rolling it back on failure —
 * mirroring the Active Sessions screen's revoke flow.
 */
@HiltViewModel
class BlockedMutedViewModel @Inject constructor(
    private val repository: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BlockedMutedUiState())
    val uiState: StateFlow<BlockedMutedUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val blocked = repository.getBlockedUsers()
            val muted = repository.getMutedUsers()
            // Surface the first failure; either list failing means an incomplete screen.
            val error = (blocked as? ApiResult.Failure)?.error
                ?: (muted as? ApiResult.Failure)?.error
            if (error != null) {
                _uiState.update { it.copy(isLoading = false, errorMessage = error.toUserMessage()) }
                return@launch
            }
            _uiState.update {
                it.copy(
                    blocked = (blocked as ApiResult.Success).data,
                    muted = (muted as ApiResult.Success).data,
                    isLoading = false,
                )
            }
        }
    }

    /** Un-blocks [username], removing the row optimistically and rolling back on failure. */
    fun unblock(username: String) {
        val state = _uiState.value
        val target = state.blocked.firstOrNull { it.username == username } ?: return
        if (username in state.pendingIds) return

        _uiState.update {
            it.copy(
                blocked = it.blocked.filterNot { u -> u.username == username },
                pendingIds = it.pendingIds + username,
                errorMessage = null,
            )
        }
        viewModelScope.launch {
            when (val result = repository.unblockUser(username)) {
                is ApiResult.Success -> _uiState.update { it.copy(pendingIds = it.pendingIds - username) }
                is ApiResult.Failure -> rollback(target, isBlocked = true, error = result.error.toUserMessage())
            }
        }
    }

    /** Un-mutes [username], removing the row optimistically and rolling back on failure. */
    fun unmute(username: String) {
        val state = _uiState.value
        val target = state.muted.firstOrNull { it.username == username } ?: return
        if (username in state.pendingIds) return

        _uiState.update {
            it.copy(
                muted = it.muted.filterNot { u -> u.username == username },
                pendingIds = it.pendingIds + username,
                errorMessage = null,
            )
        }
        viewModelScope.launch {
            when (val result = repository.unmuteUser(username)) {
                is ApiResult.Success -> _uiState.update { it.copy(pendingIds = it.pendingIds - username) }
                is ApiResult.Failure -> rollback(target, isBlocked = false, error = result.error.toUserMessage())
            }
        }
    }

    /** Restores an optimistically-removed row to the end of its list and surfaces the error. */
    private fun rollback(user: ModeratedUser, isBlocked: Boolean, error: String) {
        _uiState.update {
            if (isBlocked) {
                it.copy(
                    blocked = it.blocked + user,
                    pendingIds = it.pendingIds - user.username,
                    errorMessage = error,
                )
            } else {
                it.copy(
                    muted = it.muted + user,
                    pendingIds = it.pendingIds - user.username,
                    errorMessage = error,
                )
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
}
