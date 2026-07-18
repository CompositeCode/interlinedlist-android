package com.interlinedlist.android.feature.profile.ui.follow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.profile.data.ProfileRepository
import com.interlinedlist.android.feature.profile.domain.FollowUser
import com.interlinedlist.android.feature.profile.ui.common.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** UI state for the pending follow-requests screen. */
data class FollowRequestsUiState(
    val requests: List<FollowUser> = emptyList(),
    val isLoading: Boolean = true,
    // Ids currently being approved/rejected, so their row can show progress.
    val pendingActionIds: Set<String> = emptySet(),
    val errorMessage: String? = null,
) {
    /** A load finished with no requests and no error. */
    val isEmpty: Boolean get() = requests.isEmpty() && !isLoading && errorMessage == null
}

/**
 * Drives the current user's pending follow requests (private accounts). Loads via
 * `GET /api/follow/requests` and approves/rejects each, removing the row on success.
 */
@HiltViewModel
class FollowRequestsViewModel @Inject constructor(
    private val repository: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FollowRequestsUiState())
    val uiState: StateFlow<FollowRequestsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.getFollowRequests()) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(requests = result.data, isLoading = false)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    fun approve(userId: String) = act(userId) { repository.approveFollowRequest(it) }

    fun reject(userId: String) = act(userId) { repository.rejectFollowRequest(it) }

    /** Runs an approve/reject [action], flipping per-row progress and dropping the row on success. */
    private fun act(userId: String, action: suspend (String) -> ApiResult<Unit>) {
        if (userId in _uiState.value.pendingActionIds) return
        _uiState.update { it.copy(pendingActionIds = it.pendingActionIds + userId, errorMessage = null) }
        viewModelScope.launch {
            when (val result = action(userId)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        requests = it.requests.filterNot { user -> user.id == userId },
                        pendingActionIds = it.pendingActionIds - userId,
                    )
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(
                        pendingActionIds = it.pendingActionIds - userId,
                        errorMessage = result.error.toUserMessage(),
                    )
                }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
}
