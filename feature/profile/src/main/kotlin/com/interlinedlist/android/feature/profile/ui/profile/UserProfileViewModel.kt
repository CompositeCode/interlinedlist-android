package com.interlinedlist.android.feature.profile.ui.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.profile.data.ProfileRepository
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
                is ApiResult.Success -> _uiState.update {
                    it.copy(user = result.data, isLoading = false)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
}
