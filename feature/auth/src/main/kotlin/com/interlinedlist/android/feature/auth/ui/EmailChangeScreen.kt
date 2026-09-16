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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.interlinedlist.android.feature.auth.nav.EmailChangeAction

/** Stable test tags for the email-change confirm / undo screen. */
object EmailChangeTestTags {
    const val HEADING = "emailChangeHeading"
    const val BODY = "emailChangeBody"
    const val PROGRESS = "emailChangeProgress"
    const val DONE = "emailChangeDone"
}

/** Hilt-wired entry point; action + token arrive as deep-link nav arguments. */
@Composable
fun EmailChangeRoute(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EmailChangeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    EmailChangeScreen(state = state, onDone = onDone, modifier = modifier)
}

/**
 * Stateless result screen for both emailed email-change links.
 *
 * The copy is deliberately explicit about *which* address won: an undo is a security
 * affordance, so the screen spells out that the previous address has been restored
 * and tells a user who did not start the change what to do next.
 */
@Composable
fun EmailChangeScreen(
    state: EmailChangeUiState,
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

            Text(
                text = headingFor(state),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag(EmailChangeTestTags.HEADING),
            )

            if (state.status == EmailChangeStatus.WORKING) {
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp).testTag(EmailChangeTestTags.PROGRESS),
                )
            }

            val failed = state.status == EmailChangeStatus.FAILED
            val body = if (failed) state.message ?: bodyFor(state) else bodyFor(state)
            if (body != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (failed || state.status == EmailChangeStatus.INVALID_LINK) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().testTag(EmailChangeTestTags.BODY),
                )
            }

            if (state.status != EmailChangeStatus.WORKING) {
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth().testTag(EmailChangeTestTags.DONE),
                ) {
                    Text("Done")
                }
            }
        }
    }
}

/** Heading for the current action + status combination. */
private fun headingFor(state: EmailChangeUiState): String = when (state.action) {
    EmailChangeAction.VERIFY -> when (state.status) {
        EmailChangeStatus.WORKING -> "Confirming your new email…"
        EmailChangeStatus.DONE -> "Email address updated"
        EmailChangeStatus.FAILED -> "Couldn't confirm the new email"
        EmailChangeStatus.INVALID_LINK -> "This confirmation link is incomplete"
    }
    EmailChangeAction.UNDO -> when (state.status) {
        EmailChangeStatus.WORKING -> "Undoing the email change…"
        EmailChangeStatus.DONE -> "Email change undone"
        EmailChangeStatus.FAILED -> "Couldn't undo the email change"
        EmailChangeStatus.INVALID_LINK -> "This undo link is incomplete"
    }
}

/** Supporting copy; the failure case prefers the server's own message. */
private fun bodyFor(state: EmailChangeUiState): String? = when (state.action) {
    EmailChangeAction.VERIFY -> when (state.status) {
        EmailChangeStatus.WORKING -> null
        EmailChangeStatus.DONE ->
            "Your account now uses your new email address. Sign in with it from now on."
        EmailChangeStatus.FAILED ->
            "The link may have expired or already been used. Request the change again " +
                "from Account settings."
        EmailChangeStatus.INVALID_LINK ->
            "It's missing its security token, so nothing was changed. Open the link " +
                "straight from the email instead of copying it by hand."
    }
    EmailChangeAction.UNDO -> when (state.status) {
        EmailChangeStatus.WORKING ->
            "This restores the email address your account had before the change."
        EmailChangeStatus.DONE ->
            "Your account email has been restored to the previous address and the " +
                "change was cancelled. If you did not request that change, someone " +
                "else may have access to your account — change your password now."
        EmailChangeStatus.FAILED ->
            "The link may have expired or already been used. Contact support if you " +
                "did not request the email change."
        EmailChangeStatus.INVALID_LINK ->
            "It's missing its security token, so nothing was changed. Open the link " +
                "straight from the email instead of copying it by hand."
    }
}

@Preview(showBackground = true)
@Composable
private fun EmailChangeVerifiedPreview() {
    InterlinedListTheme {
        EmailChangeScreen(
            state = EmailChangeUiState(
                action = EmailChangeAction.VERIFY,
                status = EmailChangeStatus.DONE,
            ),
            onDone = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EmailChangeUndonePreview() {
    InterlinedListTheme {
        EmailChangeScreen(
            state = EmailChangeUiState(
                action = EmailChangeAction.UNDO,
                status = EmailChangeStatus.DONE,
            ),
            onDone = {},
        )
    }
}
