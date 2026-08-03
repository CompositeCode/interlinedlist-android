package com.interlinedlist.android.feature.auth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.interlinedlist.android.core.designsystem.component.InterlinedListWordmark
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme

/** Stable test tags for the reset-password controls. */
object ResetPasswordTestTags {
    const val PASSWORD = "resetPassword"
    const val CONFIRM = "resetConfirm"
    const val SUBMIT = "resetSubmit"
    const val ERROR = "resetError"
    const val MISMATCH = "resetMismatch"
    const val MISSING_TOKEN = "resetMissingToken"
    const val PROGRESS = "resetProgress"
    const val BACK = "resetBack"
}

/** Hilt-wired entry point; the token is supplied via the deep-link nav argument. */
@Composable
fun ResetPasswordRoute(
    onReset: () -> Unit,
    onBackToLogin: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ResetPasswordViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ResetPasswordScreen(
        state = state,
        onPasswordChange = viewModel::onPasswordChange,
        onConfirmPasswordChange = viewModel::onConfirmPasswordChange,
        onSubmit = { viewModel.submit(onReset) },
        onBackToLogin = onBackToLogin,
        modifier = modifier,
    )
}

/** Stateless reset-password UI. */
@Composable
fun ResetPasswordScreen(
    state: ResetPasswordUiState,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBackToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val showMismatch = state.confirmPassword.isNotEmpty() && !state.passwordsMatch
    val hasToken = state.token.isNotBlank()

    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            InterlinedListWordmark()
            Spacer(Modifier.height(24.dp))
            Text("Choose a new password", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(24.dp))

            if (!hasToken) {
                Text(
                    text = "This reset link is missing or invalid. Request a new one from " +
                        "the sign-in screen.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().testTag(ResetPasswordTestTags.MISSING_TOKEN),
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onBackToLogin,
                    modifier = Modifier.fillMaxWidth().testTag(ResetPasswordTestTags.BACK),
                ) {
                    Text("Back to sign in")
                }
                return@Column
            }

            OutlinedTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = { Text("New password") },
                singleLine = true,
                enabled = !state.isLoading,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth().testTag(ResetPasswordTestTags.PASSWORD),
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.confirmPassword,
                onValueChange = onConfirmPasswordChange,
                label = { Text("Confirm new password") },
                singleLine = true,
                isError = showMismatch,
                enabled = !state.isLoading,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth().testTag(ResetPasswordTestTags.CONFIRM),
            )

            if (showMismatch) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Passwords don't match.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().testTag(ResetPasswordTestTags.MISMATCH),
                )
            }

            if (state.errorMessage != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = state.errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().testTag(ResetPasswordTestTags.ERROR),
                )
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onSubmit,
                enabled = state.canSubmit,
                modifier = Modifier.fillMaxWidth().testTag(ResetPasswordTestTags.SUBMIT),
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp).testTag(ResetPasswordTestTags.PROGRESS),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Reset password")
                }
            }

            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = onBackToLogin,
                enabled = !state.isLoading,
                modifier = Modifier.testTag(ResetPasswordTestTags.BACK),
            ) {
                Text("Back to sign in")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ResetPasswordScreenPreview() {
    InterlinedListTheme {
        ResetPasswordScreen(
            state = ResetPasswordUiState(token = "abc", password = "secret", confirmPassword = "secret"),
            onPasswordChange = {},
            onConfirmPasswordChange = {},
            onSubmit = {},
            onBackToLogin = {},
        )
    }
}
