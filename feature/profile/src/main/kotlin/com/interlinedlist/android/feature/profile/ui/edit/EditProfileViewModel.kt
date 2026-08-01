package com.interlinedlist.android.feature.profile.ui.edit

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

/** UI state for the edit-profile form. */
data class EditProfileUiState(
    val displayName: String = "",
    val bio: String = "",
    val avatarUrl: String? = null,
    // Text field for the "set avatar from URL" affordance.
    val avatarUrlInput: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isUploadingAvatar: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val errorMessage: String? = null,
) {
    val canSave: Boolean get() = hasUnsavedChanges && !isSaving && !isLoading
}

/**
 * Backs the edit-profile form. Seeds from the current user's Room cache, edits the
 * display name and bio, saves via `PATCH /api/user/update`, and sets the avatar via
 * upload or from a URL.
 */
@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val repository: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditProfileUiState())
    val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()

    init {
        seedFromCache()
        refresh()
    }

    /** Seeds the form from the cached current user so it renders instantly offline. */
    private fun seedFromCache() {
        viewModelScope.launch {
            repository.observeCurrentUser().collect { cached ->
                if (cached != null && !_uiState.value.hasUnsavedChanges) {
                    _uiState.update {
                        it.copy(
                            displayName = cached.displayName ?: "",
                            bio = cached.bio ?: "",
                            avatarUrl = cached.avatarUrl,
                        )
                    }
                }
            }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            when (val result = repository.refreshCurrentUser()) {
                is ApiResult.Success -> _uiState.update {
                    if (it.hasUnsavedChanges) {
                        it.copy(isLoading = false)
                    } else {
                        it.copy(
                            displayName = result.data.displayName ?: "",
                            bio = result.data.bio ?: "",
                            avatarUrl = result.data.avatarUrl,
                            isLoading = false,
                        )
                    }
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    fun onDisplayNameChange(value: String) =
        _uiState.update { it.copy(displayName = value, hasUnsavedChanges = true, errorMessage = null) }

    fun onBioChange(value: String) =
        _uiState.update { it.copy(bio = value, hasUnsavedChanges = true, errorMessage = null) }

    fun onAvatarUrlInputChange(value: String) =
        _uiState.update { it.copy(avatarUrlInput = value, errorMessage = null) }

    /** Persists display name + bio; invokes [onSaved] on success. */
    fun save(onSaved: () -> Unit = {}) {
        val state = _uiState.value
        if (!state.canSave) return
        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            val result = repository.updateProfile(
                displayName = state.displayName.trim(),
                bio = state.bio.trim(),
            )
            when (result) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isSaving = false, hasUnsavedChanges = false) }
                    onSaved()
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isSaving = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    /** Sets the avatar from the URL currently typed into the form. */
    fun setAvatarFromUrl() {
        val url = _uiState.value.avatarUrlInput.trim()
        if (url.isBlank()) return
        _uiState.update { it.copy(isUploadingAvatar = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.setAvatarFromUrl(url)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        isUploadingAvatar = false,
                        avatarUrl = result.data.avatarUrl,
                        avatarUrlInput = "",
                    )
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isUploadingAvatar = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    /** Uploads a picked image (already resolved to bytes) as the new avatar. */
    fun uploadAvatar(bytes: ByteArray, fileName: String, mimeType: String) {
        _uiState.update { it.copy(isUploadingAvatar = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.uploadAvatar(bytes, fileName, mimeType)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isUploadingAvatar = false, avatarUrl = result.data.avatarUrl)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isUploadingAvatar = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
}
