package com.interlinedlist.android.feature.profile.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.profile.data.ProfileRepository
import com.interlinedlist.android.feature.profile.ui.common.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the Account settings screen (change email + delete account).
 *
 * [username] is seeded from the current user's cache and is the value the
 * type-to-confirm delete guard checks against. [pendingEmail] mirrors the server's
 * `pendingEmail` field: non-null while an email change is awaiting confirmation from
 * the link mailed to that address, and null again once it is confirmed or undone.
 */
data class AccountSettingsUiState(
    val username: String = "",
    val isChangingEmail: Boolean = false,
    val isDeletingAccount: Boolean = false,
    // A one-shot confirmation to show after a successful email-change request.
    val emailChangeRequested: Boolean = false,
    /** The address an email change is waiting on, or null when none is in flight. */
    val pendingEmail: String? = null,
    val isResendingEmailChange: Boolean = false,
    /** A one-shot confirmation to show after the verification email is re-sent. */
    val emailChangeResent: Boolean = false,
    val errorMessage: String? = null,
)

/** One-shot effects the Account settings screen reacts to. */
sealed interface AccountSettingsEffect {
    /** The account was deleted; the app should sign the user out and leave the tab. */
    data object SignedOut : AccountSettingsEffect
}

/**
 * Drives the Account settings screen. Requests an email change via
 * `POST /api/user/change-email/request` and deletes the account via
 * `POST /api/user/delete`. On a successful delete it emits [AccountSettingsEffect.SignedOut]
 * so the app can clear the session and navigate away (the profile module does not own
 * session state).
 *
 * The pending-change banner is re-read from the server on every entry rather than
 * cached, so it disappears as soon as the change is confirmed (or undone) from the
 * emailed link — which happens outside this screen, and possibly on another device.
 */
@HiltViewModel
class AccountSettingsViewModel @Inject constructor(
    private val repository: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountSettingsUiState())
    val uiState: StateFlow<AccountSettingsUiState> = _uiState.asStateFlow()

    private val _effects = Channel<AccountSettingsEffect>(Channel.BUFFERED)
    val effects: Flow<AccountSettingsEffect> = _effects.receiveAsFlow()

    init {
        seedFromCache()
        refreshPendingEmailChange()
    }

    /**
     * Re-reads `pendingEmail` from `GET /api/user`. Called on entry and after a
     * request/resend so the banner reflects the server, not a local guess.
     */
    fun refreshPendingEmailChange() {
        viewModelScope.launch {
            when (val result = repository.getPendingEmailChange()) {
                is ApiResult.Success -> _uiState.update { it.copy(pendingEmail = result.data) }
                // A failed read is not worth an error banner on a settings screen:
                // leave whatever is on screen alone and try again next entry.
                is ApiResult.Failure -> Unit
            }
        }
    }

    /** Seeds [AccountSettingsUiState.username] from the cached current user. */
    private fun seedFromCache() {
        viewModelScope.launch {
            repository.observeCurrentUser().collect { cached ->
                if (cached != null) {
                    _uiState.update { it.copy(username = cached.username) }
                }
            }
        }
    }

    /** Requests a verification email to move the account to [newEmail]. */
    fun requestEmailChange(newEmail: String) {
        val email = newEmail.trim()
        if (email.isBlank() || _uiState.value.isChangingEmail) return
        _uiState.update { it.copy(isChangingEmail = true, emailChangeRequested = false, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.requestEmailChange(email)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isChangingEmail = false,
                            emailChangeRequested = true,
                            pendingEmail = email,
                        )
                    }
                    refreshPendingEmailChange()
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isChangingEmail = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    /**
     * Re-sends the confirmation email for the change already in flight, by re-issuing
     * the same `POST /api/user/change-email/request` for the pending address.
     */
    fun resendEmailChange() {
        val pending = _uiState.value.pendingEmail
        if (pending.isNullOrBlank() || _uiState.value.isResendingEmailChange) return
        _uiState.update {
            it.copy(isResendingEmailChange = true, emailChangeResent = false, errorMessage = null)
        }
        viewModelScope.launch {
            when (val result = repository.requestEmailChange(pending)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isResendingEmailChange = false, emailChangeResent = true)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(
                        isResendingEmailChange = false,
                        errorMessage = result.error.toUserMessage(),
                    )
                }
            }
        }
    }

    /**
     * Deletes the account after the type-to-confirm guard passed. Requires the account's
     * [email] (the server verifies both username and email). Emits
     * [AccountSettingsEffect.SignedOut] on success.
     */
    fun deleteAccount(email: String) {
        val state = _uiState.value
        if (state.isDeletingAccount || state.username.isBlank()) return
        _uiState.update { it.copy(isDeletingAccount = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.deleteAccount(username = state.username, email = email.trim())) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isDeletingAccount = false) }
                    _effects.send(AccountSettingsEffect.SignedOut)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isDeletingAccount = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }

    fun acknowledgeEmailChange() = _uiState.update { it.copy(emailChangeRequested = false) }

    fun acknowledgeEmailChangeResent() = _uiState.update { it.copy(emailChangeResent = false) }
}
