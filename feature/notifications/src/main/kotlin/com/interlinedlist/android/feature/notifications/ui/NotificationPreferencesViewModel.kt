package com.interlinedlist.android.feature.notifications.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.notifications.data.NotificationPreferencesRepository
import com.interlinedlist.android.feature.notifications.domain.NotificationChannel
import com.interlinedlist.android.feature.notifications.domain.NotificationPreference
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Notification-preferences screen state: the loaded per-event preferences plus
 * transient loading/error flags. The event list itself is the source of truth here
 * (there is no cache), so it lives directly in the state.
 */
data class NotificationPreferencesUiState(
    val preferences: List<NotificationPreference> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    val isEmpty: Boolean get() = preferences.isEmpty()
}

@HiltViewModel
class NotificationPreferencesViewModel @Inject constructor(
    private val repository: NotificationPreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationPreferencesUiState())
    val uiState: StateFlow<NotificationPreferencesUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /** Loads (or reloads) the preferences from the API. */
    fun refresh() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.getPreferences()) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(preferences = result.data, isLoading = false, errorMessage = null)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.error.toPreferencesMessage())
                }
            }
        }
    }

    /**
     * Flips one [channel] of the event identified by [key] to [enabled], updating the
     * UI optimistically and persisting via the repository. On failure the change is
     * rolled back to its previous value and an error is surfaced. Toggling a channel
     * the event does not support is a no-op.
     */
    fun onToggle(key: String, channel: NotificationChannel, enabled: Boolean) {
        val previous = _uiState.value.preferences.firstOrNull { it.key == key } ?: return
        // The event doesn't offer this channel — nothing to change or send.
        if (channel !in previous.channels) return
        if (previous.isEnabled(channel) == enabled) return

        val updated = previous.withChannel(channel, enabled)
        // Optimistic: reflect the new value immediately.
        replacePreference(updated)

        viewModelScope.launch {
            val result = repository.updatePreference(updated)
            if (result is ApiResult.Failure) {
                // Roll back to the pre-toggle value and surface the error.
                replacePreference(previous)
                surface(result.error)
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(errorMessage = null) }

    private fun replacePreference(preference: NotificationPreference) {
        _uiState.update { state ->
            state.copy(
                preferences = state.preferences.map {
                    if (it.key == preference.key) preference else it
                },
            )
        }
    }

    private fun surface(error: AppError) =
        _uiState.update { it.copy(errorMessage = error.toPreferencesMessage()) }
}
