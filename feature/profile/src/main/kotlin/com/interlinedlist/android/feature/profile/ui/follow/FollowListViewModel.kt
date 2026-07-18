package com.interlinedlist.android.feature.profile.ui.follow

import androidx.lifecycle.SavedStateHandle
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

/** Nav arg key the followers/following routes read their target username from. */
const val FOLLOW_USERNAME_ARG = "username"

/** UI state for a followers or following list. */
data class FollowListUiState(
    val users: List<FollowUser> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
) {
    /** A load finished with no users and no error. */
    val isEmpty: Boolean get() = users.isEmpty() && !isLoading && errorMessage == null
}

/**
 * Base for the followers and following list ViewModels. Reads the target [username]
 * from the nav arg (routes `followers/{username}` / `following/{username}`) and loads
 * the list via [loadUsers]. Tapping a row drills down into that user's profile.
 */
abstract class FollowListViewModel(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    protected val username: String = checkNotNull(savedStateHandle[FOLLOW_USERNAME_ARG]) {
        "FollowListViewModel requires a '$FOLLOW_USERNAME_ARG' nav arg"
    }

    private val _uiState = MutableStateFlow(FollowListUiState())
    val uiState: StateFlow<FollowListUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /** Fetches the appropriate list (followers or following) for [username]. */
    protected abstract suspend fun loadUsers(username: String): ApiResult<List<FollowUser>>

    fun refresh() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = loadUsers(username)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(users = result.data, isLoading = false)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
}

/** Drives the followers list (`GET /api/follow/{userId}/followers`). */
@HiltViewModel
class FollowersViewModel @Inject constructor(
    private val repository: ProfileRepository,
    savedStateHandle: SavedStateHandle,
) : FollowListViewModel(savedStateHandle) {
    override suspend fun loadUsers(username: String) = repository.getFollowers(username)
}

/** Drives the following list (`GET /api/follow/{userId}/following`). */
@HiltViewModel
class FollowingViewModel @Inject constructor(
    private val repository: ProfileRepository,
    savedStateHandle: SavedStateHandle,
) : FollowListViewModel(savedStateHandle) {
    override suspend fun loadUsers(username: String) = repository.getFollowing(username)
}
