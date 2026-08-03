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

/** Nav argument key for the reset token delivered by the deep link. */
const val RESET_TOKEN_ARG = "token"

/** UI state for the reset-password screen. */
data class ResetPasswordUiState(
    /** Token from the emailed reset link (may be blank if opened without one). */
    val token: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    val passwordsMatch: Boolean get() = password == confirmPassword

    val canSubmit: Boolean
        get() = token.isNotBlank() &&
            password.isNotBlank() &&
            confirmPassword.isNotBlank() &&
            passwordsMatch &&
            !isLoading
}

@HiltViewModel
class ResetPasswordViewModel(
    private val authRepository: AuthRepository,
    token: String?,
) : ViewModel() {

    /** Hilt entry point: pulls the token from the deep-link nav argument. */
    @Inject
    constructor(
        authRepository: AuthRepository,
        savedStateHandle: SavedStateHandle,
    ) : this(authRepository, savedStateHandle.get<String>(RESET_TOKEN_ARG))

    private val _uiState = MutableStateFlow(ResetPasswordUiState(token = token.orEmpty()))
    val uiState: StateFlow<ResetPasswordUiState> = _uiState.asStateFlow()

    fun onPasswordChange(value: String) =
        _uiState.update { it.copy(password = value, errorMessage = null) }

    fun onConfirmPasswordChange(value: String) =
        _uiState.update { it.copy(confirmPassword = value, errorMessage = null) }

    /** Completes the reset; on success [onReset] returns the user to login. */
    fun submit(onReset: () -> Unit) {
        val current = _uiState.value
        if (!current.canSubmit) {
            if (!current.passwordsMatch) {
                _uiState.update { it.copy(errorMessage = "Passwords don't match.") }
            }
            return
        }
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = authRepository.resetPassword(
                token = current.token,
                newPassword = current.password,
            )
            when (result) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isLoading = false) }
                    onReset()
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }
}
