package com.interlinedlist.android.feature.auth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.interlinedlist.android.core.designsystem.component.InterlinedListWordmark
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme

/** Stable test tags for the verify-email controls. */
object VerifyEmailTestTags {
    const val STATUS = "verifyStatus"
    const val MESSAGE = "verifyMessage"
    const val RESEND = "verifyResend"
    const val PROGRESS = "verifyProgress"
    const val CONTINUE = "verifyContinue"
    const val BACK = "verifyBack"
}

/** Hilt-wired entry point; the token (if any) is supplied via the deep-link nav argument. */
@Composable
fun VerifyEmailRoute(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VerifyEmailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    VerifyEmailScreen(
        state = state,
        onResend = viewModel::resend,
        onDone = onDone,
        modifier = modifier,
    )
}

/** Stateless verify-email UI: confirms a deep-link token and/or offers a resend. */
@Composable
fun VerifyEmailScreen(
    state: VerifyEmailUiState,
    onResend: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            InterlinedListWordmark()
            Spacer(Modifier.height(24.dp))

            val heading = when (state.status) {
                VerifyEmailStatus.VERIFYING -> "Verifying your email…"
                VerifyEmailStatus.VERIFIED -> "Email verified"
                VerifyEmailStatus.FAILED -> "Couldn't verify your email"
                VerifyEmailStatus.IDLE -> "Verify your email"
            }
            Text(
                text = heading,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag(VerifyEmailTestTags.STATUS),
            )

            if (state.status == VerifyEmailStatus.VERIFYING) {
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp).testTag(VerifyEmailTestTags.PROGRESS),
                )
            }

            val body = state.message ?: when (state.status) {
                VerifyEmailStatus.IDLE ->
                    "We've sent a verification link to your email. Didn't get it? " +
                        "Resend it below."
                else -> null
            }
            if (body != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = body,
                    color = if (state.status == VerifyEmailStatus.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().testTag(VerifyEmailTestTags.MESSAGE),
                )
            }

            Spacer(Modifier.height(24.dp))
            if (state.status == VerifyEmailStatus.VERIFIED) {
                Button(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth().testTag(VerifyEmailTestTags.CONTINUE),
                ) {
                    Text("Continue")
                }
            } else {
                OutlinedButton(
                    onClick = onResend,
                    enabled = !state.isResending,
                    modifier = Modifier.fillMaxWidth().testTag(VerifyEmailTestTags.RESEND),
                ) {
                    if (state.isResending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Resend verification email")
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = onDone,
                    modifier = Modifier.testTag(VerifyEmailTestTags.BACK),
                ) {
                    Text("Back to sign in")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun VerifyEmailIdlePreview() {
    InterlinedListTheme {
        VerifyEmailScreen(
            state = VerifyEmailUiState(status = VerifyEmailStatus.IDLE),
            onResend = {},
            onDone = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VerifyEmailVerifiedPreview() {
    InterlinedListTheme {
        VerifyEmailScreen(
            state = VerifyEmailUiState(
                status = VerifyEmailStatus.VERIFIED,
                message = "Your email is verified. You're all set.",
            ),
            onResend = {},
            onDone = {},
        )
    }
}
