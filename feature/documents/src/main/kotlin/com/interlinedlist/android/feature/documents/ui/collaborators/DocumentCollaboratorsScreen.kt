package com.interlinedlist.android.feature.documents.ui.collaborators

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.interlinedlist.android.feature.documents.domain.Collaborator
import com.interlinedlist.android.feature.documents.domain.CollaboratorCandidate
import com.interlinedlist.android.feature.documents.domain.CollaboratorRole
import com.interlinedlist.android.feature.documents.domain.DocumentInvite
import com.interlinedlist.android.feature.documents.domain.InviteRole
import com.interlinedlist.android.feature.documents.domain.InviteStatus
import com.interlinedlist.android.feature.documents.ui.common.inviteExpiryLabel

/** Stable test tags for the Manage-access sheet. */
object DocumentCollaboratorsTestTags {
    const val SHEET = "collabSheet"
    const val LIST = "collabList"
    const val EMPTY = "collabEmpty"
    const val PROGRESS = "collabProgress"
    const val ERROR = "collabError"
    const val SEARCH_FIELD = "collabSearchField"
    const val SEARCH_BUTTON = "collabSearchButton"
    fun row(userId: String) = "collabRow_$userId"
    fun revoke(userId: String) = "collabRevoke_$userId"
    fun role(userId: String, role: CollaboratorRole) = "collabRole_${userId}_${role.apiValue}"
    fun candidate(userId: String) = "collabCandidate_$userId"
    fun inviteRole(role: CollaboratorRole) = "collabInviteRole_${role.apiValue}"

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
 * Hilt-wired "Manage access" sheet, shown as a modal bottom sheet over the editor.
 * Reads its `documentId` from the nav SavedStateHandle (see [COLLABORATORS_DOCUMENT_ID_ARG]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentCollaboratorsRoute(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DocumentCollaboratorsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier.testTag(DocumentCollaboratorsTestTags.SHEET),
    ) {
        DocumentCollaboratorsSheetContent(
            state = state,
            onSearchQueryChange = viewModel::onSearchQueryChange,
            onSearch = viewModel::searchUsers,
            onSelectInviteRole = viewModel::selectRole,
            onInvite = viewModel::invite,
            onChangeRole = viewModel::changeRole,
            onRevoke = viewModel::revoke,
            onInviteEmailChange = viewModel::onInviteEmailChange,
            onSelectInviteEmailRole = viewModel::selectInviteRole,
            onSendInvite = viewModel::sendInvite,
            onRevokeInvite = viewModel::revokeInvite,
        )
    }
}

/** Stateless Manage-access body: current collaborators + role controls + invite search. */
@Composable
fun DocumentCollaboratorsSheetContent(
    state: DocumentCollaboratorsUiState,
    onSearchQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSelectInviteRole: (CollaboratorRole) -> Unit,
    onInvite: (CollaboratorCandidate) -> Unit,
    onChangeRole: (String, CollaboratorRole) -> Unit,
    onRevoke: (String) -> Unit,
    onInviteEmailChange: (String) -> Unit,
    onSelectInviteEmailRole: (InviteRole) -> Unit,
    onSendInvite: () -> Unit,
    onRevokeInvite: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Outlined.Group, contentDescription = null)
            Text("Manage access", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Invite people and choose what they can do with this document.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (state.errorMessage != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = state.errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag(DocumentCollaboratorsTestTags.ERROR),
            )
        }

        // --- Invite -------------------------------------------------------
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchQueryChange,
                label = { Text("Search people") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag(DocumentCollaboratorsTestTags.SEARCH_FIELD),
            )
            IconButton(
                onClick = onSearch,
                modifier = Modifier.testTag(DocumentCollaboratorsTestTags.SEARCH_BUTTON),
            ) { Icon(Icons.Outlined.Search, contentDescription = "Search") }
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CollaboratorRole.entries.forEach { role ->
                FilterChip(
                    selected = state.selectedRole == role,
                    onClick = { onSelectInviteRole(role) },
                    label = { Text(role.label) },
                    modifier = Modifier.testTag(DocumentCollaboratorsTestTags.inviteRole(role)),
                )
            }
        }

        if (state.candidates.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(state.candidates, key = { it.userId }) { candidate ->
                    CandidateRow(candidate = candidate, onInvite = { onInvite(candidate) })
                }
            }
        }

        // --- Current collaborators ---------------------------------------
        Spacer(Modifier.height(16.dp))
        Text("People with access", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        when {
            state.isLoading -> Box(
                Modifier.fillMaxWidth().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(Modifier.testTag(DocumentCollaboratorsTestTags.PROGRESS)) }

            state.isEmpty -> Text(
                text = "No collaborators yet. Search above to invite someone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(DocumentCollaboratorsTestTags.EMPTY),
            )

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .testTag(DocumentCollaboratorsTestTags.LIST),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.collaborators, key = { it.userId }) { collaborator ->
                    CollaboratorRow(
                        collaborator = collaborator,
                        onChangeRole = { role -> onChangeRole(collaborator.userId, role) },
                        onRevoke = { onRevoke(collaborator.userId) },
                    )
                }
            }
        }

        // --- Email invites ------------------------------------------------
        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        InviteByEmailSection(
            state = state.invites,
            onEmailChange = onInviteEmailChange,
            onSelectRole = onSelectInviteEmailRole,
            onSend = onSendInvite,
            onRevoke = onRevokeInvite,
        )
    }
}

/**
 * "Invite by email" — the form for inviting an address that need not have an
 * account yet, plus the pending invites it produces. Sending is a subscriber
 * feature; listing and revoking are always available.
 */
@Composable
private fun InviteByEmailSection(
    state: DocumentInvitesUiState,
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
                "and the document stays private.",
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
                .testTag(DocumentCollaboratorsTestTags.INVITE_EMAIL_FIELD),
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
                    modifier = Modifier.testTag(DocumentCollaboratorsTestTags.inviteEmailRole(role)),
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onSend,
            enabled = state.canSend,
            modifier = Modifier.testTag(DocumentCollaboratorsTestTags.INVITE_SEND),
        ) { Text(if (state.isSending) "Sending…" else "Send invite") }

        if (state.subscriptionRequired) {
            Spacer(Modifier.height(8.dp))
            Column(Modifier.testTag(DocumentCollaboratorsTestTags.INVITE_GATE)) {
                Text(
                    text = "Subscriber feature",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = state.errorMessage ?: "Subscribe to invite people to documents.",
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
                modifier = Modifier.testTag(DocumentCollaboratorsTestTags.INVITE_ERROR),
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
                modifier = Modifier.testTag(DocumentCollaboratorsTestTags.INVITE_EMPTY),
            )

            // A short, owner-managed list — a plain Column keeps it scrollable
            // inside the sheet without nesting a lazy list.
            else -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(DocumentCollaboratorsTestTags.INVITE_LIST),
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
private fun PendingInviteRow(invite: DocumentInvite, onRevoke: () -> Unit) {
    val status = invite.statusAt()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(DocumentCollaboratorsTestTags.inviteRow(invite.token)),
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
            modifier = Modifier.testTag(DocumentCollaboratorsTestTags.inviteRevoke(invite.token)),
        ) { Text("Revoke") }
    }
}

@Composable
private fun Avatar(initial: String, modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier.size(36.dp),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = initial,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun CandidateRow(candidate: CollaboratorCandidate, onInvite: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(DocumentCollaboratorsTestTags.candidate(candidate.userId)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(candidate.initial)
        Column(Modifier.weight(1f)) {
            Text(candidate.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            candidate.email?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        IconButton(onClick = onInvite) {
            Icon(Icons.Default.PersonAdd, contentDescription = "Invite ${candidate.label}")
        }
    }
}

@Composable
private fun CollaboratorRow(
    collaborator: Collaborator,
    onChangeRole: (CollaboratorRole) -> Unit,
    onRevoke: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(DocumentCollaboratorsTestTags.row(collaborator.userId)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Avatar(collaborator.initial)
            Column(Modifier.weight(1f)) {
                Text(collaborator.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                collaborator.email?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            IconButton(
                onClick = onRevoke,
                modifier = Modifier.testTag(DocumentCollaboratorsTestTags.revoke(collaborator.userId)),
            ) { Icon(Icons.Default.Close, contentDescription = "Remove ${collaborator.label}") }
        }
        Row(
            modifier = Modifier.padding(start = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CollaboratorRole.entries.forEach { role ->
                FilterChip(
                    selected = collaborator.role == role,
                    onClick = { onChangeRole(role) },
                    label = { Text(role.label) },
                    leadingIcon = if (collaborator.role == role) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    modifier = Modifier.testTag(
                        DocumentCollaboratorsTestTags.role(collaborator.userId, role),
                    ),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DocumentCollaboratorsSheetPreview() {
    InterlinedListTheme {
        DocumentCollaboratorsSheetContent(
            state = DocumentCollaboratorsUiState(
                collaborators = listOf(
                    Collaborator("u1", CollaboratorRole.ADMIN, "Ada Lovelace", "ada", "ada@x.io", null),
                    Collaborator("u2", CollaboratorRole.VIEWER, "Bob", "bob", null, null),
                ),
                isLoading = false,
                invites = DocumentInvitesUiState(
                    invites = listOf(
                        DocumentInvite(
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
            onSearchQueryChange = {},
            onSearch = {},
            onSelectInviteRole = {},
            onInvite = {},
            onChangeRole = { _, _ -> },
            onRevoke = {},
            onInviteEmailChange = {},
            onSelectInviteEmailRole = {},
            onSendInvite = {},
            onRevokeInvite = {},
        )
    }
}
