package com.interlinedlist.android.feature.organizations.ui.detail

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import com.interlinedlist.android.feature.organizations.domain.MemberCandidate
import com.interlinedlist.android.feature.organizations.domain.OrgMember
import com.interlinedlist.android.feature.organizations.domain.OrgRole
import com.interlinedlist.android.feature.organizations.domain.Organization

/** Stable test tags for the organization detail screen. */
object OrganizationDetailTestTags {
    const val LIST = "orgDetailMembers"
    const val SEARCH = "orgDetailMemberSearch"
    const val EMPTY = "orgDetailEmpty"
    const val PROGRESS = "orgDetailProgress"
    const val ERROR = "orgDetailError"
    const val SUBSCRIPTION = "orgDetailSubscription"
    const val OVERFLOW = "orgDetailOverflow"
    const val EDIT = "orgDetailEdit"
    const val DELETE = "orgDetailDelete"
    const val EDIT_DIALOG = "orgDetailEditDialog"
    const val DELETE_DIALOG = "orgDetailDeleteDialog"
    const val DELETE_CONFIRM = "orgDetailDeleteConfirm"
    fun member(userId: String) = "orgMember_$userId"
    fun remove(userId: String) = "orgMemberRemove_$userId"
    fun candidate(userId: String) = "orgCandidate_$userId"
}

/**
 * Hilt-wired entry for a single organization. Reads its `orgId` from the nav
 * SavedStateHandle (see [ORG_ID_ARG]); [onBack] and [onDeleted] let the app pop
 * navigation after viewing or deleting the org.
 */
@Composable
fun OrganizationDetailRoute(
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OrganizationDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    OrganizationDetailScreen(
        state = state,
        onBack = onBack,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onAddCandidate = { viewModel.addMember(it) },
        onChangeRole = viewModel::changeRole,
        onRemoveMember = viewModel::removeMember,
        onSaveEdit = { name, description, isPublic -> viewModel.updateOrganization(name, description, isPublic) },
        onDelete = { viewModel.deleteOrganization(onDeleted) },
        modifier = modifier,
    )
}

/** Stateless organization detail — metadata header + members management. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizationDetailScreen(
    state: OrganizationDetailUiState,
    onBack: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onAddCandidate: (MemberCandidate) -> Unit,
    onChangeRole: (OrgMember, OrgRole) -> Unit,
    onRemoveMember: (OrgMember) -> Unit,
    onSaveEdit: (name: String?, description: String?, isPublic: Boolean?) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(state.title.ifBlank { "Organization" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier.testTag(OrganizationDetailTestTags.OVERFLOW),
                    ) { Icon(Icons.Default.MoreVert, contentDescription = "More actions") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = { menuOpen = false; showEdit = true },
                            modifier = Modifier.testTag(OrganizationDetailTestTags.EDIT),
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = { menuOpen = false; showDeleteConfirm = true },
                            modifier = Modifier.testTag(OrganizationDetailTestTags.DELETE),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.subscriptionRequired -> Centered(
                Modifier.padding(padding).testTag(OrganizationDetailTestTags.SUBSCRIPTION),
            ) {
                Text("Subscribers only", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(state.errorMessage ?: "Organizations require an active subscription.")
            }

            state.isLoading -> Centered(Modifier.padding(padding)) {
                CircularProgressIndicator(Modifier.testTag(OrganizationDetailTestTags.PROGRESS))
            }

            state.organization == null && state.errorMessage != null -> Centered(
                Modifier.padding(padding).testTag(OrganizationDetailTestTags.ERROR),
            ) { Text(state.errorMessage) }

            else -> Column(Modifier.padding(padding)) {
                state.organization?.let { org -> OrganizationHeader(org) }

                if (state.errorMessage != null) {
                    Text(
                        text = state.errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .testTag(OrganizationDetailTestTags.ERROR),
                    )
                }

                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchQueryChange,
                    label = { Text("Add a member") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag(OrganizationDetailTestTags.SEARCH),
                )

                MemberList(
                    members = state.members,
                    candidates = state.candidates,
                    isEmpty = state.isEmpty,
                    onAddCandidate = onAddCandidate,
                    onChangeRole = onChangeRole,
                    onRemoveMember = onRemoveMember,
                )
            }
        }
    }

    if (showEdit && state.organization != null) {
        EditOrganizationDialog(
            organization = state.organization,
            onDismiss = { showEdit = false },
            onConfirm = { name, description, isPublic ->
                showEdit = false
                onSaveEdit(name, description, isPublic)
            },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            modifier = Modifier.testTag(OrganizationDetailTestTags.DELETE_DIALOG),
            title = { Text("Delete organization?") },
            text = { Text("This permanently removes \"${state.title}\" and its membership. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = { showDeleteConfirm = false; onDelete() },
                    modifier = Modifier.testTag(OrganizationDetailTestTags.DELETE_CONFIRM),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun OrganizationHeader(org: Organization) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (!org.description.isNullOrBlank()) {
            Text(
                text = org.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "${org.memberCount} ${if (org.memberCount == 1) "member" else "members"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = if (org.isPublic) "Public" else "Private",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun MemberList(
    members: List<OrgMember>,
    candidates: List<MemberCandidate>,
    isEmpty: Boolean,
    onAddCandidate: (MemberCandidate) -> Unit,
    onChangeRole: (OrgMember, OrgRole) -> Unit,
    onRemoveMember: (OrgMember) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag(OrganizationDetailTestTags.LIST),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (candidates.isNotEmpty()) {
            item { Text("Suggestions", style = MaterialTheme.typography.labelLarge) }
            items(candidates, key = { "candidate-${it.userId}" }) { candidate ->
                CandidateRow(candidate = candidate, onAdd = { onAddCandidate(candidate) })
            }
        }

        if (isEmpty && candidates.isEmpty()) {
            item { EmptyState() }
        } else {
            items(members, key = { it.userId }) { member ->
                MemberRow(
                    member = member,
                    onChangeRole = { onChangeRole(member, it) },
                    onRemove = { onRemoveMember(member) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemberRow(
    member: OrgMember,
    onChangeRole: (OrgRole) -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(OrganizationDetailTestTags.member(member.userId)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = member.label,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "@${member.username}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.testTag(OrganizationDetailTestTags.remove(member.userId)),
                ) { Icon(Icons.Default.Close, contentDescription = "Remove member") }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OrgRole.entries.forEach { role ->
                    FilterChip(
                        selected = member.role == role,
                        onClick = { onChangeRole(role) },
                        label = { Text(role.label) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CandidateRow(candidate: MemberCandidate, onAdd: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(OrganizationDetailTestTags.candidate(candidate.userId)),
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
private fun EditOrganizationDialog(
    organization: Organization,
    onDismiss: () -> Unit,
    onConfirm: (name: String?, description: String?, isPublic: Boolean?) -> Unit,
) {
    var name by remember { mutableStateOf(organization.name) }
    var description by remember { mutableStateOf(organization.description.orEmpty()) }
    var isPublic by remember { mutableStateOf(organization.isPublic) }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.testTag(OrganizationDetailTestTags.EDIT_DIALOG)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Edit organization", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
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
                        onClick = { onConfirm(name.trim(), description, isPublic) },
                        enabled = name.isNotBlank(),
                    ) { Text("Save") }
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp)
            .testTag(OrganizationDetailTestTags.EMPTY),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No members yet", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Search above to add someone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Centered(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, content = { content() })
    }
}

@Preview(showBackground = true)
@Composable
private fun OrganizationDetailScreenPreview() {
    InterlinedListTheme {
        OrganizationDetailScreen(
            state = OrganizationDetailUiState(
                organization = Organization("1", "Acme Corp", "We make everything", null, false, 2, OrgRole.OWNER, null),
                members = listOf(
                    OrgMember("u1", "ada", "Ada Lovelace", null, OrgRole.OWNER, active = true),
                    OrgMember("u2", "grace", null, null, OrgRole.MEMBER, active = true),
                ),
                isLoading = false,
            ),
            onBack = {},
            onSearchQueryChange = {},
            onAddCandidate = {},
            onChangeRole = { _, _ -> },
            onRemoveMember = {},
            onSaveEdit = { _, _, _ -> },
            onDelete = {},
        )
    }
}
