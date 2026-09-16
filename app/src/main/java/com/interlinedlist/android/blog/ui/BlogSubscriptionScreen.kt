package com.interlinedlist.android.blog.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.interlinedlist.android.blog.BlogSubscriptionAction
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme

/** Stable test tags for the blog email-list screen. */
object BlogSubscriptionTestTags {
    const val BACK = "blogSubscriptionBack"
    const val HEADING = "blogSubscriptionHeading"
    const val BODY = "blogSubscriptionBody"
    const val EMAIL = "blogSubscriptionEmail"
    const val SUBMIT = "blogSubscriptionSubmit"
    const val CHANGE_EMAIL = "blogSubscriptionChangeEmail"
    const val PROGRESS = "blogSubscriptionProgress"
    const val UNSUBSCRIBE_HINT = "blogSubscriptionUnsubscribeHint"
}

/** Hilt-wired entry point; the link action + token arrive as nav arguments. */
@Composable
fun BlogSubscriptionRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BlogSubscriptionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BlogSubscriptionScreen(
        state = state,
        onEmailChange = viewModel::onEmailChange,
        onSubscribe = viewModel::subscribe,
        onUseDifferentEmail = viewModel::onUseDifferentEmail,
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * Stateless blog email-list UI: the subscribe form, and the result of either link a
 * blog email can send the user back with.
 *
 * The double opt-in is stated *before* the user subscribes and again after, because a
 * subscribe screen that looks finished while the address is still unconfirmed is the
 * whole failure mode this flow has: the user walks away, no posts ever arrive, and
 * nothing in the app ever said why.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BlogSubscriptionScreen(
    state: BlogSubscriptionUiState,
    onEmailChange: (String) -> Unit,
    onSubscribe: () -> Unit,
    onUseDifferentEmail: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Blog emails") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag(BlogSubscriptionTestTags.BACK),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
        ) {
            Text(
                text = headingFor(state),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag(BlogSubscriptionTestTags.HEADING),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = state.message?.takeIf { state.status == BlogSubscriptionStatus.FAILED }
                    ?: bodyFor(state),
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.status == BlogSubscriptionStatus.FAILED ||
                    state.status == BlogSubscriptionStatus.INVALID_LINK
                ) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.fillMaxWidth().testTag(BlogSubscriptionTestTags.BODY),
            )

            if (state.status == BlogSubscriptionStatus.WORKING) {
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp).testTag(BlogSubscriptionTestTags.PROGRESS),
                )
            }

            when {
                // The form: shown until a confirmation email has been requested.
                state.isSubscribeForm &&
                    state.status != BlogSubscriptionStatus.CHECK_YOUR_EMAIL -> {
                    Spacer(Modifier.height(24.dp))
                    OutlinedTextField(
                        value = state.email,
                        onValueChange = onEmailChange,
                        label = { Text("Email address") },
                        singleLine = true,
                        enabled = state.status != BlogSubscriptionStatus.WORKING,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(BlogSubscriptionTestTags.EMAIL),
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onSubscribe,
                        enabled = state.canSubmit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(BlogSubscriptionTestTags.SUBMIT),
                    ) {
                        Text("Send me the confirmation email")
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = UNSUBSCRIBE_HINT,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(BlogSubscriptionTestTags.UNSUBSCRIBE_HINT),
                    )
                }

                state.status == BlogSubscriptionStatus.CHECK_YOUR_EMAIL -> {
                    Spacer(Modifier.height(24.dp))
                    OutlinedButton(
                        onClick = onUseDifferentEmail,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(BlogSubscriptionTestTags.CHANGE_EMAIL),
                    ) {
                        Text("Use a different address")
                    }
                }
            }
        }
    }
}

/**
 * Copy shown under the subscribe form. Unsubscribing needs the token that only a blog
 * email carries, so the honest instruction is to use that link — which this app now
 * opens itself.
 */
internal const val UNSUBSCRIBE_HINT =
    "To stop the emails, use the unsubscribe link at the bottom of any blog email. " +
        "This app opens those links itself, so you can unsubscribe without leaving it."

/**
 * Heading for the current state. Kept a pure function so the copy that decides whether
 * the user understands the double opt-in is covered by plain unit tests.
 */
internal fun headingFor(state: BlogSubscriptionUiState): String = when (state.status) {
    BlogSubscriptionStatus.CHECK_YOUR_EMAIL -> "Check your email"
    BlogSubscriptionStatus.CONFIRMED -> "You're subscribed"
    BlogSubscriptionStatus.UNSUBSCRIBED -> "You've been unsubscribed"
    BlogSubscriptionStatus.INVALID_LINK -> when (state.linkAction) {
        BlogSubscriptionAction.UNSUBSCRIBE -> "This unsubscribe link didn't work"
        else -> "This confirmation link didn't work"
    }
    BlogSubscriptionStatus.WORKING -> when (state.linkAction) {
        BlogSubscriptionAction.CONFIRM -> "Confirming your subscription…"
        BlogSubscriptionAction.UNSUBSCRIBE -> "Unsubscribing…"
        null -> "Sending your confirmation email…"
    }
    BlogSubscriptionStatus.FAILED -> when (state.linkAction) {
        BlogSubscriptionAction.CONFIRM -> "Couldn't confirm your subscription"
        BlogSubscriptionAction.UNSUBSCRIBE -> "Couldn't unsubscribe you"
        null -> "Couldn't send the confirmation email"
    }
    BlogSubscriptionStatus.EDITING -> "Get new posts by email"
}

/**
 * Supporting copy. The [BlogSubscriptionStatus.CHECK_YOUR_EMAIL] case is the important
 * one: it must not read like the job is done.
 */
internal fun bodyFor(state: BlogSubscriptionUiState): String = when (state.status) {
    BlogSubscriptionStatus.EDITING ->
        "We'll email you a confirmation link. You won't be subscribed, and no posts " +
            "will be sent, until you open it."
    BlogSubscriptionStatus.WORKING -> ""
    BlogSubscriptionStatus.CHECK_YOUR_EMAIL -> {
        val address = state.submittedEmail.ifBlank { "your address" }
        "We've sent a confirmation link to $address. You are not subscribed yet — open " +
            "that email and tap the link to finish. Nothing will arrive until you do. " +
            "If it isn't there in a few minutes, check your spam folder."
    }
    BlogSubscriptionStatus.CONFIRMED ->
        "Your email address is confirmed. New posts will arrive as they are published."
    BlogSubscriptionStatus.UNSUBSCRIBED ->
        "Your email address has been removed from the blog mailing list. You can " +
            "subscribe again at any time."
    BlogSubscriptionStatus.INVALID_LINK -> when (state.linkAction) {
        BlogSubscriptionAction.UNSUBSCRIBE ->
            "The link may have expired or already been used. Open the unsubscribe link " +
                "in a more recent blog email, straight from the email rather than by " +
                "copying it."
        else ->
            "The link may have expired or already been used. Start again from " +
                "Account \u203a Blog emails to get a fresh confirmation email, and open " +
                "the link straight from that email."
    }
    // The server's own message replaces this; it is the fallback when there is none.
    BlogSubscriptionStatus.FAILED -> "Something went wrong. Please try again."
}

@Preview(showBackground = true)
@Composable
private fun BlogSubscriptionFormPreview() {
    InterlinedListTheme {
        BlogSubscriptionScreen(
            state = BlogSubscriptionUiState(email = "reader@example.com"),
            onEmailChange = {},
            onSubscribe = {},
            onUseDifferentEmail = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BlogSubscriptionCheckEmailPreview() {
    InterlinedListTheme {
        BlogSubscriptionScreen(
            state = BlogSubscriptionUiState(
                status = BlogSubscriptionStatus.CHECK_YOUR_EMAIL,
                email = "reader@example.com",
                submittedEmail = "reader@example.com",
            ),
            onEmailChange = {},
            onSubscribe = {},
            onUseDifferentEmail = {},
            onBack = {},
        )
    }
}
