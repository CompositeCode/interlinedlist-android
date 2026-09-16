package com.interlinedlist.android.feature.profile.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.profile.data.SettingsRepository
import com.interlinedlist.android.feature.profile.domain.SettingsBounds
import com.interlinedlist.android.feature.profile.domain.UserSettings
import com.interlinedlist.android.feature.profile.domain.UserSettingsUpdate
import com.interlinedlist.android.feature.profile.domain.ViewingPreference
import com.interlinedlist.android.feature.profile.domain.defaultPubliclyVisibleOrDefault
import com.interlinedlist.android.feature.profile.domain.isPrivateAccountOrDefault
import com.interlinedlist.android.feature.profile.domain.maxMessageLengthOrDefault
import com.interlinedlist.android.feature.profile.domain.messagesPerPageOrDefault
import com.interlinedlist.android.feature.profile.domain.notificationTrayLimitOrDefault
import com.interlinedlist.android.feature.profile.domain.showAdvancedPostSettingsOrDefault
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

    /** Chooses whether new messages start public or private. */
    fun setDefaultPubliclyVisible(publiclyVisible: Boolean) {
        val current = _uiState.value.settings ?: return
        if (current.defaultPubliclyVisibleOrDefault == publiclyVisible) return
        save(
            optimistic = current.copy(defaultPubliclyVisible = publiclyVisible),
            previous = current,
            update = UserSettingsUpdate(defaultPubliclyVisible = publiclyVisible),
        )
    }

    /** Shows or hides the composer's advanced (gear) options. */
    fun setShowAdvancedPostSettings(enabled: Boolean) {
        val current = _uiState.value.settings ?: return
        if (current.showAdvancedPostSettingsOrDefault == enabled) return
        save(
            optimistic = current.copy(showAdvancedPostSettings = enabled),
            previous = current,
            update = UserSettingsUpdate(showAdvancedPostSettings = enabled),
        )
    }

    /**
     * Makes the account private, or public again.
     *
     * Turning it on does not disturb existing followers — the help centre is explicit
     * that "Existing followers are not affected; they remain followers unless you
     * remove them" (`/help/people`, "Private accounts") — it only makes *new* follows
     * arrive as requests to approve or reject. A failed save rolls back, so the switch
     * never claims a privacy state the server did not accept.
     */
    fun setPrivateAccount(private: Boolean) {
        val current = _uiState.value.settings ?: return
        if (current.isPrivateAccountOrDefault == private) return
        save(
            optimistic = current.copy(isPrivateAccount = private),
            previous = current,
            update = UserSettingsUpdate(isPrivateAccount = private),
        )
    }

    /**
     * Sets the account's message character limit. Values outside
     * [SettingsBounds.MAX_MESSAGE_LENGTH] are refused here, so a bad number never
     * reaches the API; a value we accept may still be refused by the server, in
     * which case [save] rolls it back.
     */
    fun setMaxMessageLength(characters: Int) {
        val current = _uiState.value.settings ?: return
        if (!withinRange(characters, SettingsBounds.MAX_MESSAGE_LENGTH, "Message character limit")) return
        if (current.maxMessageLengthOrDefault == characters) return
        save(
            optimistic = current.copy(maxMessageLength = characters),
            previous = current,
            update = UserSettingsUpdate(maxMessageLength = characters),
        )
    }

    /**
     * Sets how many messages the feed loads at a time. The help centre documents the
     * supported range as 10 to 30 ([SettingsBounds.MESSAGES_PER_PAGE]); anything else
     * is refused without a request.
     */
    fun setMessagesPerPage(messages: Int) {
        val current = _uiState.value.settings ?: return
        if (!withinRange(messages, SettingsBounds.MESSAGES_PER_PAGE, "Messages per page")) return
        if (current.messagesPerPageOrDefault == messages) return
        save(
            optimistic = current.copy(messagesPerPage = messages),
            previous = current,
            update = UserSettingsUpdate(messagesPerPage = messages),
        )
    }

    /**
     * Sets how many notifications the tray holds before older ones drop off. The help
     * centre documents the supported range as 10 to 40
     * ([SettingsBounds.NOTIFICATION_TRAY_LIMIT]); anything else is refused without a
     * request.
     *
     * The saved value is not cosmetic: `:feature:notifications` sizes both its list
     * and its system-tray group by it, so a rejected save must roll back rather than
     * leave the two disagreeing about how much the tray holds.
     */
    fun setNotificationTrayLimit(notifications: Int) {
        val current = _uiState.value.settings ?: return
        if (!withinRange(
                notifications,
                SettingsBounds.NOTIFICATION_TRAY_LIMIT,
                "Notification tray limit",
            )
        ) {
            return
        }
        if (current.notificationTrayLimitOrDefault == notifications) return
        save(
            optimistic = current.copy(notificationTrayLimit = notifications),
            previous = current,
            update = UserSettingsUpdate(notificationTrayLimit = notifications),
        )
    }

    fun dismissError() = _uiState.update { it.copy(errorMessage = null) }

    /**
     * True when [value] is inside [range]; otherwise reports it as an error naming
     * the bounds and returns false, leaving the stored value alone.
     */
    private fun withinRange(value: Int, range: IntRange, label: String): Boolean {
        if (value in range) return true
        _uiState.update {
            it.copy(errorMessage = "$label must be between ${range.first} and ${range.last}.")
        }
        return false
    }

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
