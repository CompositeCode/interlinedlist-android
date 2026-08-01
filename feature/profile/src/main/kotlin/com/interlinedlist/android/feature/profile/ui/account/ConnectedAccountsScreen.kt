package com.interlinedlist.android.feature.profile.ui.account

import androidx.compose.foundation.layout.Arrangement
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
import com.interlinedlist.android.feature.profile.domain.LinkedIdentity
import com.interlinedlist.android.feature.profile.ui.common.UserAvatar

/** Stable test tags for the Connected Accounts screen. */
object ConnectedAccountsTestTags {
    const val LIST = "connectedAccountsList"
    const val EMPTY = "connectedAccountsEmpty"
    const val PROGRESS = "connectedAccountsProgress"
    const val ERROR = "connectedAccountsError"
    const val BACK = "connectedAccountsBack"
    const val CONFIRM_DIALOG = "connectedAccountsConfirmDialog"
    const val CONFIRM_UNLINK = "connectedAccountsConfirmUnlink"
    fun row(id: String) = "identityRow_$id"
    fun unlink(id: String) = "identityUnlink_$id"
}

/**
 * The current user's linked social accounts (route `account/connected`). Each identity
 * can be unlinked behind a confirm dialog.
 *
 * @param onBack pop back to the account hub.
 */
@Composable
fun ConnectedAccountsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConnectedAccountsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ConnectedAccountsScreen(
        state = state,
        onUnlink = viewModel::unlink,
        onBack = onBack,
        onRetry = viewModel::refresh,
        modifier = modifier,
    )
}

/** Stateless Connected Accounts UI. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ConnectedAccountsScreen(
    state: ConnectedAccountsUiState,
    onUnlink: (String) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingConfirm by remember { mutableStateOf<LinkedIdentity?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Connected accounts") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(ConnectedAccountsTestTags.BACK)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(
                    Modifier.align(Alignment.Center).testTag(ConnectedAccountsTestTags.PROGRESS),
                )

                state.errorMessage != null && state.identities.isEmpty() -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = state.errorMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag(ConnectedAccountsTestTags.ERROR),
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRetry) { Text("Retry") }
                }

                state.isEmpty -> Text(
                    text = "No connected accounts yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).testTag(ConnectedAccountsTestTags.EMPTY),
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag(ConnectedAccountsTestTags.LIST),
                ) {
                    items(state.identities, key = { it.id }) { identity ->
                        IdentityRow(
                            identity = identity,
                            inProgress = identity.id in state.pendingUnlinkIds,
                            onUnlink = { pendingConfirm = identity },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    pendingConfirm?.let { identity ->
        AlertDialog(
            onDismissRequest = { pendingConfirm = null },
            modifier = Modifier.testTag(ConnectedAccountsTestTags.CONFIRM_DIALOG),
            title = { Text("Unlink ${identity.providerLabel}?") },
            text = { Text("\"${identity.displayLabel}\" will be disconnected from your account.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUnlink(identity.id)
                        pendingConfirm = null
                    },
                    modifier = Modifier.testTag(ConnectedAccountsTestTags.CONFIRM_UNLINK),
                ) {
                    Text("Unlink", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingConfirm = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun IdentityRow(
    identity: LinkedIdentity,
    inProgress: Boolean,
    onUnlink: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ConnectedAccountsTestTags.row(identity.id))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(avatarUrl = identity.avatarUrl, seedLabel = identity.providerLabel, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = identity.providerLabel,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = identity.providerUsername ?: "Connected ${relativeTime(identity.connectedAt)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (inProgress) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            OutlinedButton(
                onClick = onUnlink,
                modifier = Modifier.testTag(ConnectedAccountsTestTags.unlink(identity.id)),
            ) {
                Text("Unlink")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ConnectedAccountsScreenPreview() {
    InterlinedListTheme {
        ConnectedAccountsScreen(
            state = ConnectedAccountsUiState(
                identities = listOf(
                    LinkedIdentity("1", "linkedin", "Adron Hall", null, null, "2026-06-12T07:33:42.447Z"),
                    LinkedIdentity("2", "mastodon:techhub.social", "crew@techhub.social", null, null, null),
                ),
                isLoading = false,
            ),
            onUnlink = {},
            onBack = {},
            onRetry = {},
        )
    }
}
