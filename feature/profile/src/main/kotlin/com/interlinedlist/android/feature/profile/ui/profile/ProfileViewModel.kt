package com.interlinedlist.android.feature.profile.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.profile.data.ProfileRepository
import com.interlinedlist.android.feature.profile.domain.FollowCounts
import com.interlinedlist.android.feature.profile.domain.FollowStatus
import com.interlinedlist.android.feature.profile.domain.ModerationStatus
import com.interlinedlist.android.feature.profile.domain.MutualConnections
import com.interlinedlist.android.feature.profile.domain.ProfileUser
import com.interlinedlist.android.feature.profile.ui.common.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for a profile screen (current user or another user).
 *
 * The follow fields ([followStatus], [followCounts], [isFollowActionInProgress]) are
 * populated only for another user's profile; on the account screen [followStatus] is
 * [FollowStatus.SELF]. [followCounts] doubles as the tappable follower/following
 * tallies on both screens.
 */
data class ProfileUiState(
    val user: ProfileUser? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val followStatus: FollowStatus = FollowStatus.SELF,
    val followCounts: FollowCounts = FollowCounts(),
    val isFollowActionInProgress: Boolean = false,
    // Public-content tabs — populated only on another user's profile (Milestone L).
    val selectedTab: ProfileContentTab = ProfileContentTab.POSTS,
    val content: PublicContentState = PublicContentState(),
    val mutualConnections: MutualConnections? = null,
    // Moderation — populated only on another user's profile (Milestone D). Drives the
    // overflow menu's blocked/muted state; [isModerationActionInProgress] disables it
    // while a block/mute/report is in flight.
    val moderationStatus: ModerationStatus = ModerationStatus(),
    val isModerationActionInProgress: Boolean = false,
    // A one-shot flag set after a successful report so the UI can confirm and dismiss.
    val reportSubmitted: Boolean = false,
) {
    /** No cached user and not loading — nothing to render yet. */
    val isEmpty: Boolean get() = user == null && !isLoading

    /** Whether a follow/unfollow affordance should be shown (another user, status known). */
    val canFollow: Boolean get() = followStatus != FollowStatus.SELF

    /** Whether the moderation overflow menu should be offered (another user, not yourself). */
    val canModerate: Boolean get() = user != null && !user.isCurrentUser
}

/**
 * Drives the current signed-in user's profile (the "Account" tab). Seeds instantly
 * from the Room cache, then refreshes from `GET /api/user`.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        observeCurrentUser()
        refresh()
    }

    private fun observeCurrentUser() {
        viewModelScope.launch {
            repository.observeCurrentUser().collect { cached ->
                if (cached != null) {
                    _uiState.update { it.copy(user = cached) }
                    loadCounts(cached.id)
                }
            }
        }
    }

    fun refresh() {
        _uiState.update { it.copy(isLoading = it.user == null, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.refreshCurrentUser()) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(user = result.data, isLoading = false) }
                    loadCounts(result.data.id)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    /** Loads the current user's own follower/following tallies for the tappable header. */
    private fun loadCounts(userId: String) {
        viewModelScope.launch {
            val result = repository.getFollowCounts(userId)
            if (result is ApiResult.Success) {
                _uiState.update { it.copy(followCounts = result.data) }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
}
