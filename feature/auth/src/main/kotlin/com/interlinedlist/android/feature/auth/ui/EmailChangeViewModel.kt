package com.interlinedlist.android.feature.auth.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.auth.data.AuthRepository
import com.interlinedlist.android.feature.auth.nav.EmailChangeAction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Nav argument key for which half of the flow the tapped link completes. */
const val EMAIL_CHANGE_ACTION_ARG = "action"

/** Nav argument key for the one-time token carried by the emailed link. */
const val EMAIL_CHANGE_TOKEN_ARG = "token"

/** Where the email-change confirmation stands. */
enum class EmailChangeStatus {
    /** The link carried no usable token — nothing was sent to the server. */
    INVALID_LINK,

    /** The token is being submitted. */
    WORKING,

    /** The server accepted the token. */
    DONE,

    /** The server rejected the token (expired, already used, address taken…). */
    FAILED,
}

/** UI state for the email-change confirm / undo screen. */
data class EmailChangeUiState(
    val action: EmailChangeAction = EmailChangeAction.VERIFY,
    val status: EmailChangeStatus = EmailChangeStatus.WORKING,
    /** Server-provided (or mapped) detail shown under the heading on failure. */
    val message: String? = null,
)

/**
 * Drives the screen both emailed email-change links land on.
 *
 * Both `POST /api/auth/verify-email-change` and `POST /api/auth/undo-email-change`
 * are unauthenticated (`x-auth-type: none`, empty `security` in the OpenAPI spec), so
 * the token is submitted straight away without requiring a session — the whole point
 * of the undo link is that whoever reaches for it may no longer be able to sign in.
 *
 * A link with no token never reaches the network: it reports [EmailChangeStatus.INVALID_LINK]
 * so a truncated or hand-edited URL can't be mistaken for a success.
 */
@HiltViewModel
class EmailChangeViewModel(
    private val authRepository: AuthRepository,
    action: EmailChangeAction,
    token: String?,
) : ViewModel() {

    /** Hilt entry point: reads the action + token from the deep-link nav arguments. */
    @Inject
    constructor(
        authRepository: AuthRepository,
        savedStateHandle: SavedStateHandle,
    ) : this(
        authRepository = authRepository,
        action = actionFrom(savedStateHandle.get<String>(EMAIL_CHANGE_ACTION_ARG)),
        token = savedStateHandle.get<String>(EMAIL_CHANGE_TOKEN_ARG),
    )

    private val _uiState = MutableStateFlow(EmailChangeUiState(action = action))
    val uiState: StateFlow<EmailChangeUiState> = _uiState.asStateFlow()

    init {
        val trimmed = token?.trim()
        if (trimmed.isNullOrEmpty()) {
            _uiState.update { it.copy(status = EmailChangeStatus.INVALID_LINK) }
        } else {
            submit(action, trimmed)
        }
    }

    private fun submit(action: EmailChangeAction, token: String) {
        _uiState.update { it.copy(status = EmailChangeStatus.WORKING, message = null) }
        viewModelScope.launch {
            val result = when (action) {
                EmailChangeAction.VERIFY -> authRepository.verifyEmailChange(token)
                EmailChangeAction.UNDO -> authRepository.undoEmailChange(token)
            }
            when (result) {
                is ApiResult.Success -> _uiState.update { it.copy(status = EmailChangeStatus.DONE) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(
                        status = EmailChangeStatus.FAILED,
                        message = result.error.toUserMessage(),
                    )
                }
            }
        }
    }

    private companion object {
        /** Unknown/missing nav values default to the non-destructive half of the flow. */
        fun actionFrom(raw: String?): EmailChangeAction =
            EmailChangeAction.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
                ?: EmailChangeAction.VERIFY
    }
}
