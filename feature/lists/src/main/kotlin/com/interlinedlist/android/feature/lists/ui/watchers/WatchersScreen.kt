package com.interlinedlist.android.feature.lists.ui.watchers

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.lists.domain.Contributor
import com.interlinedlist.android.feature.lists.domain.InviteRole
import com.interlinedlist.android.feature.lists.domain.InviteStatus
import com.interlinedlist.android.feature.lists.domain.ListInvite
import com.interlinedlist.android.feature.lists.domain.Watcher
import com.interlinedlist.android.feature.lists.domain.WatcherCandidate
import com.interlinedlist.android.feature.lists.domain.WatcherRole
import com.interlinedlist.android.feature.lists.ui.inviteExpiryLabel

/** Stable test tags for the watchers screen. */
object WatchersTestTags {
    const val LIST = "watchersList"
    const val SEARCH = "watchersSearch"
    const val EMPTY = "watchersEmpty"
    const val PROGRESS = "watchersProgress"
    const val ERROR = "watchersError"
    const val CONTRIBUTORS = "watchersContributors"
    fun watcher(userId: String) = "watcher_$userId"
    fun remove(userId: String) = "watcherRemove_$userId"
    fun candidate(userId: String) = "watcherCandidate_$userId"
    fun contributor(userId: String) = "contributor_$userId"

    // Email invites.
    const val INVITE_EMAIL_FIELD = "inviteEmailField"
    const val INVITE_SEND = "inviteSend"
    const val INVITE_LIST = "inviteList"
    const val INVITE_EMPTY = "inviteEmpty"
    const val INVITE_ERROR = "inviteError"
    const val INVITE_GATE = "inviteGate"
    fun inviteEmailRole(role: InviteRole) = "inviteEmailRole_${role.apiValue}"
    fun inviteRow(token: String) = "inviteRow_$token"
    fun inviteRevoke(token: String) = "inviteRevoke_$token"
}

/**
 * Hilt-wired entry for a list's watchers. Reads its `listId` from the nav
 * SavedStateHandle (see [WATCHERS_LIST_ID_ARG]); [onBack] pops navigation.
 */
@Composable
fun WatchersRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WatchersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    WatchersScreen(
        state = state,
        onBack = onBack,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onAddCandidate = { viewModel.addWatcher(it) },
        onChangeRole = viewModel::changeRole,
        onRemoveWatcher = viewModel::removeWatcher,
        onInviteEmailChange = viewModel::onInviteEmailChange,
        onSelectInviteEmailRole = viewModel::selectInviteRole,
        onSendInvite = viewModel::sendInvite,
        onRevokeInvite = viewModel::revokeInvite,
        modifier = modifier,
    )
}

/** Stateless watchers screen — list watchers, change roles, add/remove watchers. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchersScreen(
    state: WatchersUiState,
    onBack: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onAddCandidate: (WatcherCandidate) -> Unit,
    onChangeRole: (Watcher, WatcherRole) -> Unit,
    onRemoveWatcher: (Watcher) -> Unit,
    onInviteEmailChange: (String) -> Unit,
    onSelectInviteEmailRole: (InviteRole) -> Unit,
    onSendInvite: () -> Unit,
    onRevokeInvite: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Watchers") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(Modifier.testTag(WatchersTestTags.PROGRESS)) }

            else -> Column(Modifier.padding(padding)) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchQueryChange,
                    label = { Text("Add a watcher") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag(WatchersTestTags.SEARCH),
                )

                if (state.errorMessage != null) {
                    Text(
                        text = state.errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .testTag(WatchersTestTags.ERROR),
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(WatchersTestTags.LIST),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.candidates.isNotEmpty()) {
                        item {
                            Text("Suggestions", style = MaterialTheme.typography.labelLarge)
                        }
                        items(state.candidates, key = { "candidate-${it.userId}" }) { candidate ->
                            CandidateRow(candidate = candidate, onAdd = { onAddCandidate(candidate) })
                        }
                    }

                    if (state.isEmpty && state.candidates.isEmpty()) {
                        item { EmptyState() }
                    } else {
                        items(state.watchers, key = { it.userId }) { watcher ->
                            WatcherRow(
                                watcher = watcher,
                                onChangeRole = { onChangeRole(watcher, it) },
                                onRemove = { onRemoveWatcher(watcher) },
                            )
                        }
                    }

                    item {
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                        InviteByEmailSection(
                            state = state.invites,
                            onEmailChange = onInviteEmailChange,
                            onSelectRole = onSelectInviteEmailRole,
                            onSend = onSendInvite,
                            onRevoke = onRevokeInvite,
                        )
                    }

                    if (state.contributors.isNotEmpty()) {
                        item {
                            Text(
                                text = "Contributors",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .testTag(WatchersTestTags.CONTRIBUTORS),
                            )
                        }
                        items(state.contributors, key = { "contributor-${it.userId}" }) { contributor ->
                            ContributorRow(contributor = contributor)
                        }
                    }
                }
            }
        }
    }
}

/**
 * "Invite by email" — the form for inviting an address that need not have an
 * account yet, plus the pending invites it produces. Sending is a subscriber
 * feature; listing and revoking are always available.
 */
@Composable
private fun InviteByEmailSection(
    state: ListInvitesUiState,
    onEmailChange: (String) -> Unit,
    onSelectRole: (InviteRole) -> Unit,
    onSend: () -> Unit,
    onRevoke: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Outlined.MailOutline, contentDescription = null)
            Text("Invite by email", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Invite someone by email address — they don't need an account yet, " +
                "and the list stays private.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.email,
            onValueChange = onEmailChange,
            label = { Text("Email") },
            singleLine = true,
            isError = state.emailError != null,
            supportingText = state.emailError?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(WatchersTestTags.INVITE_EMAIL_FIELD),
        )

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InviteRole.entries.forEach { role ->
                FilterChip(
                    selected = state.role == role,
                    onClick = { onSelectRole(role) },
                    label = { Text(role.label) },
                    modifier = Modifier.testTag(WatchersTestTags.inviteEmailRole(role)),
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onSend,
            enabled = state.canSend,
            modifier = Modifier.testTag(WatchersTestTags.INVITE_SEND),
        ) { Text(if (state.isSending) "Sending…" else "Send invite") }

        if (state.subscriptionRequired) {
            Spacer(Modifier.height(8.dp))
            Column(Modifier.testTag(WatchersTestTags.INVITE_GATE)) {
                Text(
                    text = "Subscriber feature",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = state.errorMessage ?: "Subscribe to invite people to lists.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (state.errorMessage != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = state.errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(WatchersTestTags.INVITE_ERROR),
            )
        }

        Spacer(Modifier.height(16.dp))
        Text("Pending invites", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        when {
            state.isLoading -> CircularProgressIndicator(Modifier.size(24.dp))

            state.isEmpty -> Text(
                text = "No invites yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(WatchersTestTags.INVITE_EMPTY),
            )

            // A short, owner-managed list — a plain Column keeps it renderable inside
            // the screen's LazyColumn without nesting a second lazy list.
            else -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(WatchersTestTags.INVITE_LIST),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.invites.forEach { invite ->
                    PendingInviteRow(invite = invite, onRevoke = { onRevoke(invite.token) })
                }
            }
        }
    }
}

/** One pending invite: address, role, derived status and expiry, with Revoke. */
@Composable
private fun PendingInviteRow(invite: ListInvite, onRevoke: () -> Unit) {
    val status = invite.statusAt()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(WatchersTestTags.inviteRow(invite.token)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = invite.email,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${invite.role.label} · ${inviteExpiryLabel(invite.expiresAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AssistChip(
            onClick = {},
            enabled = false,
            label = { Text(status.label) },
            colors = AssistChipDefaults.assistChipColors(
                disabledLabelColor = when (status) {
                    InviteStatus.ACCEPTED -> MaterialTheme.colorScheme.primary
                    InviteStatus.EXPIRED, InviteStatus.REVOKED -> MaterialTheme.colorScheme.error
                    InviteStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            ),
        )
        TextButton(
            onClick = onRevoke,
            modifier = Modifier.testTag(WatchersTestTags.inviteRevoke(invite.token)),
        ) { Text("Revoke") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WatcherRow(
    watcher: Watcher,
    onChangeRole: (WatcherRole) -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(WatchersTestTags.watcher(watcher.userId)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = watcher.label,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "@${watcher.username}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.testTag(WatchersTestTags.remove(watcher.userId)),
                ) { Icon(Icons.Default.Close, contentDescription = "Remove watcher") }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WatcherRole.entries.forEach { role ->
                    FilterChip(
                        selected = watcher.role == role,
                        onClick = { onChangeRole(role) },
                        label = { Text(role.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CandidateRow(candidate: WatcherCandidate, onAdd: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(WatchersTestTags.candidate(candidate.userId)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(candidate.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "@${candidate.username}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AssistChip(
                onClick = onAdd,
                label = { Text("Add") },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
            )
        }
    }
}

@Composable
private fun ContributorRow(contributor: Contributor) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(WatchersTestTags.contributor(contributor.userId)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = contributor.label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "@${contributor.username}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "${contributor.addedCount} added · ${contributor.editedCount} edited",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp)
            .testTag(WatchersTestTags.EMPTY),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No watchers yet", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Search above to grant someone access.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WatchersScreenPreview() {
    InterlinedListTheme {
        WatchersScreen(
            state = WatchersUiState(
                watchers = listOf(
                    Watcher("u1", "ada", "Ada Lovelace", null, WatcherRole.EDITOR),
                    Watcher("u2", "grace", null, null, WatcherRole.VIEWER),
                ),
                isLoading = false,
                invites = ListInvitesUiState(
                    invites = listOf(
                        ListInvite(
                            email = "friend@example.com",
                            token = "tok-a",
                            role = InviteRole.EDITOR,
                            expiresAt = null,
                            createdAt = null,
                            accepted = false,
                            revokedAt = null,
                            url = null,
                        ),
                    ),
                    isLoading = false,
                ),
            ),
            onBack = {},
            onSearchQueryChange = {},
            onAddCandidate = {},
            onChangeRole = { _, _ -> },
            onRemoveWatcher = {},
            onInviteEmailChange = {},
            onSelectInviteEmailRole = {},
            onSendInvite = {},
            onRevokeInvite = {},
        )
    }
}
