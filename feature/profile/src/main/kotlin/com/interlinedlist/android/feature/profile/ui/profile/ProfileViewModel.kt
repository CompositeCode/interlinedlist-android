package com.interlinedlist.android.feature.profile.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.profile.data.ProfileRepository
import com.interlinedlist.android.feature.profile.domain.ProfileUser
import com.interlinedlist.android.feature.profile.ui.common.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** UI state for a profile screen (current user or another user). */
data class ProfileUiState(
    val user: ProfileUser? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
) {
    /** No cached user and not loading — nothing to render yet. */
    val isEmpty: Boolean get() = user == null && !isLoading
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
                }
            }
        }
    }

    fun refresh() {
        _uiState.update { it.copy(isLoading = it.user == null, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.refreshCurrentUser()) {
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
