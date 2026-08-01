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
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
    fun row(provider: ConnectedAccount.Provider) = "account_${provider.name}"
    fun status(provider: ConnectedAccount.Provider) = "accountStatus_${provider.name}"
}

/** Hilt-wired entry point for the read-only "Connected accounts" screen. */
@Composable
fun ConnectedAccountsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConnectedAccountsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ConnectedAccountsScreen(state = state, onBack = onBack, modifier = modifier)
}

/** Stateless "Connected accounts" UI — easy to preview and to drive from Compose tests. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectedAccountsScreen(
    state: ConnectedAccountsUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            items(state.accounts, key = { it.provider.name }) { account -> AccountRow(account) }
            item {
                Spacer(Modifier.size(4.dp))
                Text(
                    text = "Connecting or disconnecting accounts happens on the InterlinedList " +
                        "website — this app shows their current status.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AccountRow(account: ConnectedAccount) {
    Card(modifier = Modifier.fillMaxWidth().testTag(ConnectedAccountsTestTags.row(account.provider))) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val (icon, tint) = if (account.isConnected) {
                Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary
            } else {
                Icons.Outlined.Circle to MaterialTheme.colorScheme.onSurfaceVariant
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
                    color = if (account.isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(ConnectedAccountsTestTags.status(account.provider)),
                )
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
                isLoading = false,
                accounts = listOf(
                    ConnectedAccount(ConnectedAccount.Provider.GITHUB, isConnected = true, handle = "@adron"),
                    ConnectedAccount(ConnectedAccount.Provider.BLUESKY, isConnected = false),
                ),
            ),
            onBack = {},
        )
    }
}
