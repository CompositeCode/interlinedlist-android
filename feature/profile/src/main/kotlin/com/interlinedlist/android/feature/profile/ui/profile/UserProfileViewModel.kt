package com.interlinedlist.android.feature.profile.ui.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.profile.data.ProfileRepository
import com.interlinedlist.android.feature.profile.domain.FollowStatus
import com.interlinedlist.android.feature.profile.ui.common.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Nav arg key the user-profile route reads its target username from. */
const val PROFILE_USERNAME_ARG = "username"

/**
 * Drives another user's public profile, reached by drilling down from search. The
 * target [username] is read from the [SavedStateHandle] nav arg (route
 * `profile/{username}`). Seeds from the Room cache, then refreshes from
 * `GET /api/users/{username}`.
 */
@HiltViewModel
class UserProfileViewModel @Inject constructor(
    private val repository: ProfileRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val username: String = checkNotNull(savedStateHandle[PROFILE_USERNAME_ARG]) {
        "UserProfileViewModel requires a '$PROFILE_USERNAME_ARG' nav arg"
    }

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        observeUser()
        refresh()
    }

    private fun observeUser() {
        viewModelScope.launch {
            repository.observeUser(username).collect { cached ->
                if (cached != null) {
                    _uiState.update { it.copy(user = cached) }
                }
            }
        }
    }

    fun refresh() {
        _uiState.update { it.copy(isLoading = it.user == null, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.refreshUser(username)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(user = result.data, isLoading = false) }
                    loadFollow(result.data.id, result.data.isCurrentUser)
                    loadMutual(result.data.id, result.data.isCurrentUser)
                    // Load the initially-selected tab now that the username is confirmed.
                    loadTab(_uiState.value.selectedTab)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    /**
     * Switches the visible content tab, loading its data the first time it is shown.
     * Already-loaded tabs are not refetched; a tap on the current tab is a no-op.
     */
    fun selectTab(tab: ProfileContentTab) {
        if (_uiState.value.selectedTab == tab && _uiState.value.content.loadedTabs.contains(tab)) {
            return
        }
        _uiState.update { it.copy(selectedTab = tab) }
        loadTab(tab)
    }

    /** Loads a content tab once (unless already loaded), tracking loading/error per tab. */
    private fun loadTab(tab: ProfileContentTab) {
        if (_uiState.value.content.loadedTabs.contains(tab)) {
            // Show cached content immediately; nothing to fetch.
            _uiState.update { it.copy(content = it.content.copy(isLoading = false, errorMessage = null)) }
            return
        }
        _uiState.update { it.copy(content = it.content.copy(isLoading = true, errorMessage = null)) }
        viewModelScope.launch {
            when (tab) {
                ProfileContentTab.POSTS -> handleTabResult(tab, repository.getUserPosts(username)) { content, data ->
                    content.copy(posts = data)
                }
                ProfileContentTab.LISTS -> handleTabResult(tab, repository.getUserLists(username)) { content, data ->
                    content.copy(lists = data)
                }
                ProfileContentTab.DOCUMENTS -> handleTabResult(tab, repository.getUserDocuments(username)) { content, data ->
                    content.copy(documents = data)
                }
            }
        }
    }

    /** Folds a tab fetch result into the content state, tracking loaded/error per tab. */
    private fun <T> handleTabResult(
        tab: ProfileContentTab,
        result: ApiResult<T>,
        apply: (PublicContentState, T) -> PublicContentState,
    ) {
        _uiState.update { state ->
            val content = when (result) {
                is ApiResult.Success -> apply(state.content, result.data).copy(
                    isLoading = false,
                    errorMessage = null,
                    loadedTabs = state.content.loadedTabs + tab,
                )
                is ApiResult.Failure -> state.content.copy(
                    isLoading = false,
                    errorMessage = result.error.toUserMessage(),
                )
            }
            state.copy(content = content)
        }
    }

    /** Loads mutual-connection counts for another user (not fetched for your own profile). */
    private fun loadMutual(userId: String, isCurrentUser: Boolean) {
        if (isCurrentUser) return
        viewModelScope.launch {
            val result = repository.getMutualConnections(userId)
            if (result is ApiResult.Success) {
                _uiState.update { it.copy(mutualConnections = result.data) }
            }
        }
    }

    /** Loads the follow status and counts for the viewed user once its id is known. */
    private fun loadFollow(userId: String, isCurrentUser: Boolean) {
        viewModelScope.launch {
            val status = if (isCurrentUser) {
                FollowStatus.SELF
            } else {
                when (val result = repository.getFollowStatus(userId)) {
                    is ApiResult.Success -> result.data
                    is ApiResult.Failure -> FollowStatus.NOT_FOLLOWING
                }
            }
            _uiState.update { it.copy(followStatus = status) }
        }
        viewModelScope.launch {
            val result = repository.getFollowCounts(userId)
            if (result is ApiResult.Success) {
                _uiState.update { it.copy(followCounts = result.data) }
            }
        }
    }

    /** Toggles the follow relationship, optimistically flipping the button state. */
    fun toggleFollow() {
        val state = _uiState.value
        val user = state.user ?: return
        if (state.isFollowActionInProgress || state.followStatus == FollowStatus.SELF) return

        val wasFollowing = state.followStatus == FollowStatus.FOLLOWING ||
            state.followStatus == FollowStatus.REQUESTED
        _uiState.update { it.copy(isFollowActionInProgress = true, errorMessage = null) }
        viewModelScope.launch {
            val result = if (wasFollowing) {
                repository.unfollowUser(user.id)
            } else {
                repository.followUser(user.id)
            }
            when (result) {
                is ApiResult.Success -> {
                    // Re-read the authoritative status (private accounts land on REQUESTED,
                    // not FOLLOWING) and refresh the follower tally.
                    val newStatus = when (val s = repository.getFollowStatus(user.id)) {
                        is ApiResult.Success -> s.data
                        is ApiResult.Failure ->
                            if (wasFollowing) FollowStatus.NOT_FOLLOWING else FollowStatus.FOLLOWING
                    }
                    _uiState.update { it.copy(followStatus = newStatus, isFollowActionInProgress = false) }
                    refreshCounts(user.id)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isFollowActionInProgress = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    private fun refreshCounts(userId: String) {
        viewModelScope.launch {
            val result = repository.getFollowCounts(userId)
            if (result is ApiResult.Success) {
                _uiState.update { it.copy(followCounts = result.data) }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
}
