package com.interlinedlist.android.feature.profile.ui.account

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.profile.domain.LoginSession

/** Stable test tags for the Active Sessions screen. */
object SessionsTestTags {
    const val LIST = "sessionsList"
    const val EMPTY = "sessionsEmpty"
    const val PROGRESS = "sessionsProgress"
    const val ERROR = "sessionsError"
    const val BACK = "sessionsBack"
    const val CONFIRM_DIALOG = "sessionsConfirmDialog"
    const val CONFIRM_REVOKE = "sessionsConfirmRevoke"
    const val CURRENT_BADGE = "sessionsCurrentBadge"
    fun row(id: String) = "sessionRow_$id"
    fun revoke(id: String) = "sessionRevoke_$id"
}

/**
 * The current user's active login sessions (route `account/sessions`). Each non-current
 * session can be signed out (revoked) behind a confirm dialog; the device the user is on
 * shows a "This device" badge and cannot be revoked.
 *
 * @param onBack pop back to the account hub.
 */
@Composable
fun SessionsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SessionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SessionsScreen(
        state = state,
        onRevoke = viewModel::revoke,
        onBack = onBack,
        onRetry = viewModel::refresh,
        modifier = modifier,
    )
}

/** Stateless Active Sessions UI. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    state: SessionsUiState,
    onRevoke: (String) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The session queued for revoke while its confirm dialog is up.
    var pendingConfirm by remember { mutableStateOf<LoginSession?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Active sessions") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(SessionsTestTags.BACK)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(
                    Modifier.align(Alignment.Center).testTag(SessionsTestTags.PROGRESS),
                )

                state.errorMessage != null && state.sessions.isEmpty() -> ErrorState(
                    message = state.errorMessage,
                    onRetry = onRetry,
                    tag = SessionsTestTags.ERROR,
                )

                state.isEmpty -> Text(
                    text = "No active sessions.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).testTag(SessionsTestTags.EMPTY),
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag(SessionsTestTags.LIST),
                ) {
                    items(state.sessions, key = { it.id }) { session ->
                        SessionRow(
                            session = session,
                            inProgress = session.id in state.pendingRevokeIds,
                            onRevoke = { pendingConfirm = session },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    pendingConfirm?.let { session ->
        AlertDialog(
            onDismissRequest = { pendingConfirm = null },
            modifier = Modifier.testTag(SessionsTestTags.CONFIRM_DIALOG),
            title = { Text("Sign out this device?") },
            text = { Text("\"${session.displayLabel}\" will be signed out and its session revoked.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRevoke(session.id)
                        pendingConfirm = null
                    },
                    modifier = Modifier.testTag(SessionsTestTags.CONFIRM_REVOKE),
                ) {
                    Text("Sign out", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingConfirm = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SessionRow(
    session: LoginSession,
    inProgress: Boolean,
    onRevoke: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(SessionsTestTags.row(session.id))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = session.displayLabel,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (session.isCurrent) {
                    Spacer(Modifier.width(8.dp))
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text("This device") },
                        colors = AssistChipDefaults.assistChipColors(
                            disabledLabelColor = MaterialTheme.colorScheme.primary,
                        ),
                        modifier = Modifier.testTag(SessionsTestTags.CURRENT_BADGE),
                    )
                }
            }
            Text(
                text = "Last used ${relativeTime(session.lastUsedAt)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!session.isCurrent) {
            if (inProgress) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                OutlinedButton(
                    onClick = onRevoke,
                    modifier = Modifier.testTag(SessionsTestTags.revoke(session.id)),
                ) {
                    Text("Sign out")
                }
            }
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit, tag: String) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(tag),
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}

@Preview(showBackground = true)
@Composable
private fun SessionsScreenPreview() {
    InterlinedListTheme {
        SessionsScreen(
            state = SessionsUiState(
                sessions = listOf(
                    LoginSession("1", "Pixel 8 (this device)", null, "2026-07-31T21:49:00.000Z", isCurrent = true),
                    LoginSession("2", "Chrome on macOS", null, "2026-07-30T09:00:00.000Z", isCurrent = false),
                ),
                isLoading = false,
            ),
            onRevoke = {},
            onBack = {},
            onRetry = {},
        )
    }
}
