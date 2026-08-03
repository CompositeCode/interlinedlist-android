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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.documents.domain.Collaborator
import com.interlinedlist.android.feature.documents.domain.CollaboratorCandidate
import com.interlinedlist.android.feature.documents.domain.CollaboratorRole

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
            ),
            onSearchQueryChange = {},
            onSearch = {},
            onSelectInviteRole = {},
            onInvite = {},
            onChangeRole = { _, _ -> },
            onRevoke = {},
        )
    }
}
