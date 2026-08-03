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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.interlinedlist.android.core.designsystem.component.InterlinedListWordmark
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme

/** Stable test tags for the forgot-password controls. */
object ForgotPasswordTestTags {
    const val EMAIL = "forgotEmail"
    const val SUBMIT = "forgotSubmit"
    const val ERROR = "forgotError"
    const val PROGRESS = "forgotProgress"
    const val CONFIRMATION = "forgotConfirmation"
    const val BACK = "forgotBack"
}

/** Hilt-wired entry point. */
@Composable
fun ForgotPasswordRoute(
    onBackToLogin: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ForgotPasswordViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ForgotPasswordScreen(
        state = state,
        onEmailChange = viewModel::onEmailChange,
        onSubmit = viewModel::submit,
        onBackToLogin = onBackToLogin,
        modifier = modifier,
    )
}

/** Stateless forgot-password UI: an email form that becomes a confirmation. */
@Composable
fun ForgotPasswordScreen(
    state: ForgotPasswordUiState,
    onEmailChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBackToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
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

            if (state.emailSent) {
                Text(
                    text = "Check your email",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "If an account exists for that address, we've sent a link " +
                        "to reset your password.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().testTag(ForgotPasswordTestTags.CONFIRMATION),
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onBackToLogin,
                    modifier = Modifier.fillMaxWidth().testTag(ForgotPasswordTestTags.BACK),
                ) {
                    Text("Back to sign in")
                }
            } else {
                Text(
                    text = "Reset your password",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Enter your email and we'll send you a reset link.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))

                OutlinedTextField(
                    value = state.email,
                    onValueChange = onEmailChange,
                    label = { Text("Email") },
                    singleLine = true,
                    enabled = !state.isLoading,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth().testTag(ForgotPasswordTestTags.EMAIL),
                )

                if (state.errorMessage != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = state.errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().testTag(ForgotPasswordTestTags.ERROR),
                    )
                }

                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onSubmit,
                    enabled = state.canSubmit,
                    modifier = Modifier.fillMaxWidth().testTag(ForgotPasswordTestTags.SUBMIT),
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp).testTag(ForgotPasswordTestTags.PROGRESS),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("Send reset link")
                    }
                }

                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = onBackToLogin,
                    enabled = !state.isLoading,
                    modifier = Modifier.testTag(ForgotPasswordTestTags.BACK),
                ) {
                    Text("Back to sign in")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ForgotPasswordFormPreview() {
    InterlinedListTheme {
        ForgotPasswordScreen(
            state = ForgotPasswordUiState(email = "you@example.com"),
            onEmailChange = {},
            onSubmit = {},
            onBackToLogin = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ForgotPasswordSentPreview() {
    InterlinedListTheme {
        ForgotPasswordScreen(
            state = ForgotPasswordUiState(email = "you@example.com", emailSent = true),
            onEmailChange = {},
            onSubmit = {},
            onBackToLogin = {},
        )
    }
}
