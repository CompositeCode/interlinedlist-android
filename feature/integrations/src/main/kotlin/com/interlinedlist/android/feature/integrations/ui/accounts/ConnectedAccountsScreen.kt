package com.interlinedlist.android.feature.integrations.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.integrations.domain.ConnectedAccount

/** Stable test tags so UI/instrumented tests can address the accounts controls. */
object ConnectedAccountsTestTags {
    const val LIST = "accountsList"
    const val PROGRESS = "accountsProgress"
    const val SNACKBAR = "accountsSnackbar"
    const val UNLINK_DIALOG = "accountsUnlinkDialog"
    const val UNLINK_CONFIRM = "accountsUnlinkConfirm"
    const val UNLINK_CANCEL = "accountsUnlinkCancel"

    /** Rows key on the identity string, since one provider can back several identities. */
    fun row(key: String) = "account_$key"
    fun status(key: String) = "accountStatus_$key"
    fun health(key: String) = "accountHealth_$key"
    fun badge(key: String) = "accountBadge_$key"
    fun verify(key: String) = "accountVerify_$key"
    fun unlink(key: String) = "accountUnlink_$key"

    fun row(provider: ConnectedAccount.Provider) = row(provider.name)
    fun status(provider: ConnectedAccount.Provider) = status(provider.name)
}

/** Hilt-wired entry point for the "Connected accounts" screen. */
@Composable
fun ConnectedAccountsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConnectedAccountsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ConnectedAccountsScreen(
        state = state,
        onBack = onBack,
        onVerify = viewModel::verify,
        onRequestUnlink = viewModel::requestUnlink,
        onDismissUnlink = viewModel::dismissUnlinkRequest,
        onConfirmUnlink = viewModel::confirmUnlink,
        onMessageShown = viewModel::clearMessage,
        onErrorShown = viewModel::clearError,
        modifier = modifier,
    )
}

/** Stateless "Connected accounts" UI — easy to preview and to drive from Compose tests. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectedAccountsScreen(
    state: ConnectedAccountsUiState,
    onBack: () -> Unit,
    onVerify: (ConnectedAccount) -> Unit = {},
    onRequestUnlink: (ConnectedAccount) -> Unit = {},
    onDismissUnlink: () -> Unit = {},
    onConfirmUnlink: () -> Unit = {},
    onMessageShown: () -> Unit = {},
    onErrorShown: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            onMessageShown()
        }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            onErrorShown()
        }
    }

    state.unlinkCandidate?.let { candidate ->
        UnlinkConfirmationDialog(
            account = candidate,
            onConfirm = onConfirmUnlink,
            onDismiss = onDismissUnlink,
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Connected accounts") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState, modifier = Modifier.testTag(ConnectedAccountsTestTags.SNACKBAR))
        },
    ) { padding ->
        if (state.isLoading && state.accounts.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(Modifier.testTag(ConnectedAccountsTestTags.PROGRESS))
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .testTag(ConnectedAccountsTestTags.LIST),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            items(state.accounts, key = { it.key }) { account ->
                AccountRow(
                    account = account,
                    isBusy = account.key in state.pendingKeys,
                    onVerify = { onVerify(account) },
                    onUnlink = { onRequestUnlink(account) },
                )
            }
            item {
                Spacer(Modifier.size(4.dp))
                Text(
                    text = "Connecting a new account happens on the InterlinedList website. " +
                        "Verifying keeps an existing connection alive so your cross-posts keep " +
                        "going through.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AccountRow(
    account: ConnectedAccount,
    isBusy: Boolean,
    onVerify: () -> Unit,
    onUnlink: () -> Unit,
) {
    val badge = account.healthBadge()
    Card(modifier = Modifier.fillMaxWidth().testTag(ConnectedAccountsTestTags.row(account.key))) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (icon, tint) = when {
                    badge != null -> Icons.Default.WarningAmber to MaterialTheme.colorScheme.error
                    account.isConnected -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary
                    else -> Icons.Outlined.Circle to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                Spacer(Modifier.size(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(account.provider.label, style = MaterialTheme.typography.titleMedium)
                    val subtitle = when {
                        account.isConnected && account.handle != null -> "Connected · ${account.handle}"
                        account.isConnected -> "Connected"
                        else -> "Not connected"
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (account.isConnected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.testTag(ConnectedAccountsTestTags.status(account.key)),
                    )
                }
                if (badge != null) {
                    AssistChip(
                        onClick = onVerify,
                        enabled = !isBusy,
                        label = { Text(badge) },
                        colors = AssistChipDefaults.assistChipColors(
                            labelColor = MaterialTheme.colorScheme.error,
                        ),
                        modifier = Modifier.testTag(ConnectedAccountsTestTags.badge(account.key)),
                    )
                }
            }

            account.healthLine()?.let { line ->
                Spacer(Modifier.size(8.dp))
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (badge != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.testTag(ConnectedAccountsTestTags.health(account.key)),
                )
            }

            if (account.isLinked) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(Modifier.size(20.dp))
                        Spacer(Modifier.size(12.dp))
                    }
                    TextButton(
                        onClick = onVerify,
                        enabled = !isBusy,
                        modifier = Modifier.testTag(ConnectedAccountsTestTags.verify(account.key)),
                    ) { Text("Verify") }
                    TextButton(
                        onClick = onUnlink,
                        enabled = !isBusy,
                        modifier = Modifier.testTag(ConnectedAccountsTestTags.unlink(account.key)),
                    ) { Text("Unlink") }
                }
            }
        }
    }
}

/** States the consequence before the account goes away, not after. */
@Composable
private fun UnlinkConfirmationDialog(
    account: ConnectedAccount,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(ConnectedAccountsTestTags.UNLINK_DIALOG),
        title = { Text(account.unlinkTitle()) },
        text = { Text(account.unlinkMessage()) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag(ConnectedAccountsTestTags.UNLINK_CONFIRM),
            ) { Text("Unlink") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(ConnectedAccountsTestTags.UNLINK_CANCEL),
            ) { Text("Keep connected") }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun ConnectedAccountsScreenPreview() {
    InterlinedListTheme {
        ConnectedAccountsScreen(
            state = ConnectedAccountsUiState(
                isLoading = false,
                accounts = listOf(
                    ConnectedAccount(
                        provider = ConnectedAccount.Provider.GITHUB,
                        isConnected = true,
                        handle = "@adron",
                        identityProvider = "github",
                        connectedAt = "2026-08-01T00:00:00Z",
                        lastVerifiedAt = "2026-09-14T00:00:00Z",
                    ),
                    ConnectedAccount(
                        provider = ConnectedAccount.Provider.LINKEDIN,
                        isConnected = true,
                        handle = "Adron Hall",
                        identityProvider = "linkedin",
                        connectedAt = "2026-05-01T00:00:00Z",
                        lastVerifiedAt = "2026-06-01T00:00:00Z",
                    ),
                    ConnectedAccount(ConnectedAccount.Provider.BLUESKY, isConnected = false),
                ),
            ),
            onBack = {},
        )
    }
}
