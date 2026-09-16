package com.interlinedlist.android.feature.profile.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.profile.data.SettingsRepository
import com.interlinedlist.android.feature.profile.domain.UserSettings
import com.interlinedlist.android.feature.profile.domain.UserSettingsUpdate
import com.interlinedlist.android.feature.profile.domain.ViewingPreference
import com.interlinedlist.android.feature.profile.ui.common.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Settings screen state. [settings] is null until the first load succeeds (the screen
 * shows the loading/error state then); [isSaving] covers an in-flight preference write,
 * which the screen uses to keep the controls responsive but visibly pending.
 */
data class SettingsUiState(
    val settings: UserSettings? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Drives the Settings screen: loads the current user's preferences and writes a single
 * changed preference at a time as a partial PATCH. Each change is applied optimistically
 * and rolled back if the API rejects it, so a failed save never leaves the toggle lying
 * about the stored value.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // The repository cache is shared with the rest of the app, so follow it —
        // a refresh triggered elsewhere keeps this screen current.
        viewModelScope.launch {
            repository.observeSettings().collect { settings ->
                if (settings != null) _uiState.update { it.copy(settings = settings) }
            }
        }
        refresh()
    }

    /** Loads (or reloads) the settings from the API. */
    fun refresh() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.refresh()) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(settings = result.data, isLoading = false, errorMessage = null)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    /** Switches which messages the Home feed shows. */
    fun setViewingPreference(preference: ViewingPreference) {
        val current = _uiState.value.settings ?: return
        if (current.viewingPreference == preference) return
        save(
            optimistic = current.copy(viewingPreference = preference),
            previous = current,
            update = UserSettingsUpdate(viewingPreference = preference),
        )
    }

    /** Turns link-preview cards on or off. */
    fun setShowPreviews(enabled: Boolean) {
        val current = _uiState.value.settings ?: return
        if (current.showPreviews == enabled) return
        save(
            optimistic = current.copy(showPreviews = enabled),
            previous = current,
            update = UserSettingsUpdate(showPreviews = enabled),
        )
    }

    fun dismissError() = _uiState.update { it.copy(errorMessage = null) }

    private fun save(
        optimistic: UserSettings,
        previous: UserSettings,
        update: UserSettingsUpdate,
    ) {
        _uiState.update { it.copy(settings = optimistic, isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.update(update)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(settings = result.data, isSaving = false)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(
                        settings = previous,
                        isSaving = false,
                        errorMessage = result.error.toUserMessage(),
                    )
                }
            }
        }
    }
}
