package com.interlinedlist.android.feature.auth.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.auth.data.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Nav argument key for the verification token delivered by the deep link. */
const val VERIFY_TOKEN_ARG = "token"

/** Where the verify-email flow currently stands. */
enum class VerifyEmailStatus {
    /** No token to check — the screen only offers a resend action. */
    IDLE,

    /** A deep-link token is being confirmed with the server. */
    VERIFYING,

    /** The token was accepted. */
    VERIFIED,

    /** The token was rejected / expired. */
    FAILED,
}

/** UI state for the email-verification screen. */
data class VerifyEmailUiState(
    val status: VerifyEmailStatus = VerifyEmailStatus.IDLE,
    /** Success or error message describing the last verify/resend outcome. */
    val message: String? = null,
    val isResending: Boolean = false,
    /** Set once a fresh verification email has been requested. */
    val resendConfirmed: Boolean = false,
)

@HiltViewModel
class VerifyEmailViewModel(
    private val authRepository: AuthRepository,
    token: String?,
) : ViewModel() {

    /** Hilt entry point: pulls the token from the deep-link nav argument. */
    @Inject
    constructor(
        authRepository: AuthRepository,
        savedStateHandle: SavedStateHandle,
    ) : this(authRepository, savedStateHandle.get<String>(VERIFY_TOKEN_ARG))

    private val _uiState = MutableStateFlow(VerifyEmailUiState())
    val uiState: StateFlow<VerifyEmailUiState> = _uiState.asStateFlow()

    init {
        val trimmed = token?.trim()
        if (!trimmed.isNullOrEmpty()) {
            verify(trimmed)
        }
    }

    private fun verify(token: String) {
        _uiState.update { it.copy(status = VerifyEmailStatus.VERIFYING, message = null) }
        viewModelScope.launch {
            when (val result = authRepository.verifyEmail(token)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        status = VerifyEmailStatus.VERIFIED,
                        message = "Your email is verified. You're all set.",
                    )
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(
                        status = VerifyEmailStatus.FAILED,
                        message = result.error.toUserMessage(),
                    )
                }
            }
        }
    }

    /** Requests a fresh verification email for the signed-in user. */
    fun resend() {
        if (_uiState.value.isResending) return
        _uiState.update { it.copy(isResending = true, message = null, resendConfirmed = false) }
        viewModelScope.launch {
            when (val result = authRepository.resendVerificationEmail()) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        isResending = false,
                        resendConfirmed = true,
                        message = "We've sent a new verification link to your email.",
                    )
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isResending = false, message = result.error.toUserMessage())
                }
            }
        }
    }
}
