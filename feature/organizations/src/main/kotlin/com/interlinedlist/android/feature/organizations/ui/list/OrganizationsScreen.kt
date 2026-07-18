package com.interlinedlist.android.feature.organizations.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.organizations.domain.Organization

/** Stable test tags for the organizations index. */
object OrganizationsTestTags {
    const val LIST = "organizationsIndex"
    const val CREATE_FAB = "organizationsCreateFab"
    const val ERROR = "organizationsError"
    const val EMPTY = "organizationsEmpty"
    const val PROGRESS = "organizationsProgress"
    const val SUBSCRIPTION = "organizationsSubscription"
    const val CREATE_DIALOG = "organizationsCreateDialog"
    const val CREATE_NAME = "organizationsCreateName"
    const val CREATE_CONFIRM = "organizationsCreateConfirm"
    fun row(id: String) = "organizationRow_$id"
}

/**
 * Hilt-wired entry point for the Organizations index (reached from the Account
 * hub). [onOpenOrg] receives the tapped org's id so the app can push the detail
 * route; [onBack] pops back to the hub.
 */
@Composable
fun OrganizationsRoute(
    onOpenOrg: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OrganizationsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    OrganizationsScreen(
        state = state,
        onOpenOrg = onOpenOrg,
        onBack = onBack,
        onLoadMore = viewModel::loadMore,
        onCreateOrganization = { name, description, isPublic ->
            viewModel.createOrganization(name, description, isPublic, onCreated = { onOpenOrg(it.id) })
        },
        modifier = modifier,
    )
}

/** Stateless organizations index — loading / empty / error / subscription / content states. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizationsScreen(
    state: OrganizationsUiState,
    onOpenOrg: (String) -> Unit,
    onBack: () -> Unit,
    onLoadMore: () -> Unit,
    onCreateOrganization: (name: String, description: String?, isPublic: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreate by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Organizations") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            if (!state.subscriptionRequired) {
                ExtendedFloatingActionButton(
                    onClick = { showCreate = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("New") },
                    modifier = Modifier.testTag(OrganizationsTestTags.CREATE_FAB),
                )
            }
        },
    ) { padding ->
        when {
            state.subscriptionRequired -> SubscriptionGate(
                message = state.errorMessage,
                modifier = Modifier.padding(padding),
            )

            state.organizations.isEmpty() && state.isRefreshing -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(Modifier.testTag(OrganizationsTestTags.PROGRESS))
            }

            else -> Column(Modifier.padding(padding)) {
                if (state.errorMessage != null) {
                    Text(
                        text = state.errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag(OrganizationsTestTags.ERROR),
                    )
                }

                if (state.isEmpty) {
                    EmptyState()
                } else {
                    OrganizationsList(
                        organizations = state.organizations,
                        isLoadingMore = state.isLoadingMore,
                        hasMore = state.hasMore,
                        onOpenOrg = onOpenOrg,
                        onLoadMore = onLoadMore,
                    )
                }
            }
        }
    }

    if (showCreate) {
        CreateOrganizationDialog(
            onDismiss = { showCreate = false },
            onConfirm = { name, description, isPublic ->
                showCreate = false
                onCreateOrganization(name, description, isPublic)
            },
        )
    }
}

@Composable
private fun OrganizationsList(
    organizations: List<Organization>,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    onOpenOrg: (String) -> Unit,
    onLoadMore: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag(OrganizationsTestTags.LIST),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(organizations, key = { it.id }) { org ->
            OrganizationCard(org = org, onClick = { onOpenOrg(org.id) })
        }
        if (hasMore) {
            item {
                // Trigger load-more when the sentinel scrolls into view.
                LaunchedLoadMore(onLoadMore)
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    if (isLoadingMore) CircularProgressIndicator(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun LaunchedLoadMore(onLoadMore: () -> Unit) {
    LaunchedEffect(Unit) { onLoadMore() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrganizationCard(org: Organization, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(OrganizationsTestTags.row(org.id)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = org.displayName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!org.description.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = org.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${org.memberCount} ${if (org.memberCount == 1) "member" else "members"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (org.isPublic) {
                    Text(
                        text = "Public",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                org.role?.let { role ->
                    Text(
                        text = role.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateOrganizationDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String?, isPublic: Boolean) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.testTag(OrganizationsTestTags.CREATE_DIALOG)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("New organization", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(OrganizationsTestTags.CREATE_NAME),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Public", style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = isPublic, onCheckedChange = { isPublic = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(
                        onClick = { onConfirm(name.trim(), description.ifBlank { null }, isPublic) },
                        enabled = name.isNotBlank(),
                        modifier = Modifier.testTag(OrganizationsTestTags.CREATE_CONFIRM),
                    ) { Text("Create") }
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(OrganizationsTestTags.EMPTY),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No organizations yet", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Tap New to create your first one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SubscriptionGate(message: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(OrganizationsTestTags.SUBSCRIPTION),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
        ) {
            Text("Subscribers only", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                text = message ?: "Organizations require an active subscription.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun OrganizationsScreenPreview() {
    InterlinedListTheme {
        OrganizationsScreen(
            state = OrganizationsUiState(
                organizations = listOf(
                    Organization("1", "Acme Corp", "We make everything", null, false, 12, null, null),
                    Organization("2", "Open Collective", null, null, true, 4, null, null),
                ),
            ),
            onOpenOrg = {},
            onBack = {},
            onLoadMore = {},
            onCreateOrganization = { _, _, _ -> },
        )
    }
}
