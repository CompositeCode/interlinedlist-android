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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
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
import com.interlinedlist.android.feature.organizations.domain.OrgLinkedInPage
import com.interlinedlist.android.feature.organizations.domain.OrgLinkedInStatus
import com.interlinedlist.android.feature.organizations.domain.OrgMember
import com.interlinedlist.android.feature.organizations.domain.OrgRole
import com.interlinedlist.android.feature.organizations.domain.Organization
import com.interlinedlist.android.feature.organizations.ui.LAST_OWNER_EXPLANATION
import com.interlinedlist.android.feature.organizations.ui.LINKEDIN_DISCONNECT_CONSEQUENCE
import com.interlinedlist.android.feature.organizations.ui.LINKEDIN_NOT_CONNECTED_EXPLANATION

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
    const val JOIN = "orgDetailJoin"
    const val JOIN_PROMPT = "orgDetailJoinPrompt"
    const val LEAVE = "orgDetailLeave"
    const val LEAVE_DIALOG = "orgDetailLeaveDialog"
    const val LEAVE_CONFIRM = "orgDetailLeaveConfirm"
    const val LAST_OWNER_NOTICE = "orgDetailLastOwnerNotice"
    const val MEMBER_LAST_OWNER_NOTICE = "orgMemberLastOwnerNotice"
    const val VISIBILITY = "orgDetailVisibility"
    const val VIEWER_ROLE = "orgDetailViewerRole"
    const val SYSTEM = "orgDetailSystem"
    const val EDIT_VISIBILITY = "orgDetailEditVisibility"
    const val LINKEDIN = "orgDetailLinkedIn"
    const val LINKEDIN_CONNECTED = "orgDetailLinkedInConnected"
    const val LINKEDIN_NOT_CONNECTED = "orgDetailLinkedInNotConnected"
    const val LINKEDIN_ERROR = "orgDetailLinkedInError"
    const val LINKEDIN_SYNC = "orgDetailLinkedInSync"
    const val LINKEDIN_NO_PAGES = "orgDetailLinkedInNoPages"
    const val LINKEDIN_DISCONNECT = "orgDetailLinkedInDisconnect"
    const val LINKEDIN_DISCONNECT_DIALOG = "orgDetailLinkedInDisconnectDialog"
    const val LINKEDIN_DISCONNECT_CONFIRM = "orgDetailLinkedInDisconnectConfirm"
    fun linkedInPage(pageId: String) = "orgLinkedInPage_$pageId"

    /** The assignment control on a member's LinkedIn row. */
    fun linkedInAssignment(userId: String) = "orgLinkedInAssignment_$userId"

    /** A page option inside a member's assignment menu. */
    fun linkedInPageOption(userId: String, pageId: String) = "orgLinkedInOption_${userId}_$pageId"

    /** The "Not assigned" option inside a member's assignment menu. */
    fun linkedInClearOption(userId: String) = "orgLinkedInOptionNone_$userId"

    fun member(userId: String) = "orgMember_$userId"
    fun remove(userId: String) = "orgMemberRemove_$userId"
    fun candidate(userId: String) = "orgCandidate_$userId"

    /** A tappable role chip on a member row; absent when that role is not assignable. */
    fun roleChip(userId: String, role: OrgRole) = "orgMemberRole_${userId}_${role.apiValue}"

    /** The read-only role label shown when the viewer may not change this member's role. */
    fun roleLabel(userId: String) = "orgMemberRoleLabel_$userId"
}

/**
 * Hilt-wired entry for a single organization. Reads its `orgId` from the nav
 * SavedStateHandle (see [ORG_ID_ARG]); [onBack], [onDeleted] and [onLeft] let the
 * app pop navigation after viewing, deleting, or leaving the org.
 */
@Composable
fun OrganizationDetailRoute(
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    onLeft: () -> Unit,
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
        onJoin = viewModel::join,
        onLeave = { viewModel.leave(onLeft) },
        onAssignLinkedInPage = viewModel::assignLinkedInPage,
        onSyncLinkedInPages = viewModel::syncLinkedInPages,
        onRemoveLinkedInCredential = viewModel::removeLinkedInCredential,
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
    onJoin: () -> Unit,
    onLeave: () -> Unit,
    onAssignLinkedInPage: (OrgMember, String?) -> Unit,
    onSyncLinkedInPages: () -> Unit,
    onRemoveLinkedInCredential: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    val permissions = state.permissions

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
                    // Only the actions this role may take are offered; with none
                    // left there is nothing to open, so the menu itself is dropped.
                    if (permissions.hasAnyOrganizationAction) {
                        IconButton(
                            onClick = { menuOpen = true },
                            modifier = Modifier.testTag(OrganizationDetailTestTags.OVERFLOW),
                        ) { Icon(Icons.Default.MoreVert, contentDescription = "More actions") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            if (permissions.canEditOrganization) {
                                DropdownMenuItem(
                                    text = { Text("Edit") },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    onClick = { menuOpen = false; showEdit = true },
                                    modifier = Modifier.testTag(OrganizationDetailTestTags.EDIT),
                                )
                            }
                            if (permissions.canDeleteOrganization) {
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                    onClick = { menuOpen = false; showDeleteConfirm = true },
                                    modifier = Modifier.testTag(OrganizationDetailTestTags.DELETE),
                                )
                            }
                            if (permissions.canLeave) {
                                DropdownMenuItem(
                                    text = { Text("Leave organization") },
                                    leadingIcon = {
                                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                                    },
                                    onClick = { menuOpen = false; showLeaveConfirm = true },
                                    modifier = Modifier.testTag(OrganizationDetailTestTags.LEAVE),
                                )
                            }
                        }
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

                // The members endpoint is members-only, so a non-member is offered
                // the join action instead of member management.
                if (permissions.canViewMembers) {
                    // Only owners and admins may add a member, so only they get the
                    // picker; everyone else sees the roster read-only.
                    if (permissions.canAddMember) {
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
                    }

                    MemberList(
                        state = state,
                        onAddCandidate = onAddCandidate,
                        onChangeRole = onChangeRole,
                        onRemoveMember = onRemoveMember,
                        // Only an owner or admin manages the shared credential, so
                        // the section exists only for them.
                        header = if (state.showLinkedIn) {
                            {
                                LinkedInSection(
                                    state = state,
                                    onAssignPage = onAssignLinkedInPage,
                                    onSync = onSyncLinkedInPages,
                                    onDisconnect = onRemoveLinkedInCredential,
                                )
                            }
                        } else {
                            null
                        },
                    )
                } else {
                    JoinPrompt(canJoin = state.canJoin, isJoining = state.isJoining, onJoin = onJoin)
                }
            }
        }
    }

    if (showEdit && state.organization != null && permissions.canEditOrganization) {
        EditOrganizationDialog(
            organization = state.organization,
            onDismiss = { showEdit = false },
            onConfirm = { name, description, isPublic ->
                showEdit = false
                onSaveEdit(name, description, isPublic)
            },
        )
    }

    if (showLeaveConfirm) {
        LeaveOrganizationDialog(
            organizationName = state.title,
            isLastOwner = state.isLastOwner,
            onDismiss = { showLeaveConfirm = false },
            onConfirm = { showLeaveConfirm = false; onLeave() },
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

/**
 * Confirmation before leaving. A sole owner is told why they cannot leave (the
 * organization would be orphaned, and the server refuses with 400) and is offered
 * no destructive action — only a way out of the dialog.
 */
@Composable
private fun LeaveOrganizationDialog(
    organizationName: String,
    isLastOwner: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(OrganizationDetailTestTags.LEAVE_DIALOG),
        title = { Text(if (isLastOwner) "You're the last owner" else "Leave organization?") },
        text = {
            if (isLastOwner) {
                Text(
                    text = LAST_OWNER_EXPLANATION,
                    modifier = Modifier.testTag(OrganizationDetailTestTags.LAST_OWNER_NOTICE),
                )
            } else {
                Text(
                    "You'll lose access to \"$organizationName\". " +
                        "You can join again while it stays public.",
                )
            }
        },
        confirmButton = {
            if (isLastOwner) {
                TextButton(onClick = onDismiss) { Text("Got it") }
            } else {
                TextButton(
                    onClick = onConfirm,
                    modifier = Modifier.testTag(OrganizationDetailTestTags.LEAVE_CONFIRM),
                ) { Text("Leave") }
            }
        },
        dismissButton = {
            if (!isLastOwner) TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** Shown to a non-member: join a public org, or explain that a private one needs an invite. */
@Composable
private fun JoinPrompt(canJoin: Boolean, isJoining: Boolean, onJoin: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .testTag(OrganizationDetailTestTags.JOIN_PROMPT),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (canJoin) "You're not a member yet" else "Members only",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (canJoin) {
                "Join to see its members and take part."
            } else {
                "This organization is private. Ask an owner or admin to add you."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (canJoin) {
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onJoin,
                enabled = !isJoining,
                modifier = Modifier.testTag(OrganizationDetailTestTags.JOIN),
            ) { Text(if (isJoining) "Joining…" else "Join") }
        }
    }
}

/**
 * Metadata header. Visibility is stated outright, with the help centre's own
 * wording for what it means ("Public: Anyone can see and join" / "Private:
 * Invite-only; members must be added by an owner or admin"), because it decides
 * who can find and join the organization.
 */
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
            org.role?.let { role ->
                Text(
                    text = "Your role: ${role.label}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(OrganizationDetailTestTags.VIEWER_ROLE),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (org.isPublic) {
                "Public · anyone can see and join"
            } else {
                "Private · invite-only; an owner or admin adds members"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.testTag(OrganizationDetailTestTags.VISIBILITY),
        )
        if (org.isSystem) {
            Text(
                text = "Built-in organization · everyone belongs to it",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(OrganizationDetailTestTags.SYSTEM),
            )
        }
    }
}

@Composable
private fun MemberList(
    state: OrganizationDetailUiState,
    onAddCandidate: (MemberCandidate) -> Unit,
    onChangeRole: (OrgMember, OrgRole) -> Unit,
    onRemoveMember: (OrgMember) -> Unit,
    /** Optional content above the roster; the screen uses it for LinkedIn. */
    header: (@Composable () -> Unit)? = null,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag(OrganizationDetailTestTags.LIST),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        header?.let { item { it() } }
        if (state.candidates.isNotEmpty()) {
            item { Text("Suggestions", style = MaterialTheme.typography.labelLarge) }
            items(state.candidates, key = { "candidate-${it.userId}" }) { candidate ->
                CandidateRow(candidate = candidate, onAdd = { onAddCandidate(candidate) })
            }
        }

        if (state.isEmpty && state.candidates.isEmpty()) {
            item { EmptyState(canAddMember = state.permissions.canAddMember) }
        } else {
            items(state.members, key = { it.userId }) { member ->
                MemberRow(
                    member = member,
                    assignableRoles = state.assignableRolesFor(member),
                    canRemove = state.canRemove(member) && !state.isOnlyOwner(member),
                    isOnlyOwner = state.isOnlyOwner(member),
                    onChangeRole = { onChangeRole(member, it) },
                    onRemove = { onRemoveMember(member) },
                )
            }
        }
    }
}

/**
 * One member. The role chips are limited to what the viewer may actually assign —
 * an admin cannot touch an owner or hand out ownership — and the organization's
 * only owner is offered no demotion at all, because the server refuses it. When the
 * viewer may change nothing, the role is shown as a plain label instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemberRow(
    member: OrgMember,
    assignableRoles: List<OrgRole>,
    canRemove: Boolean,
    isOnlyOwner: Boolean,
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
                if (canRemove) {
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.testTag(OrganizationDetailTestTags.remove(member.userId)),
                    ) { Icon(Icons.Default.Close, contentDescription = "Remove member") }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (assignableRoles.isEmpty()) {
                Text(
                    text = member.role.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(OrganizationDetailTestTags.roleLabel(member.userId)),
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    assignableRoles.forEach { role ->
                        FilterChip(
                            selected = member.role == role,
                            onClick = { onChangeRole(role) },
                            label = { Text(role.label) },
                            modifier = Modifier.testTag(
                                OrganizationDetailTestTags.roleChip(member.userId, role),
                            ),
                        )
                    }
                }
            }
            if (isOnlyOwner) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "The only owner can't be demoted or removed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(OrganizationDetailTestTags.MEMBER_LAST_OWNER_NOTICE),
                )
            }
        }
    }
}

/**
 * The organization's shared LinkedIn credential: whether one is connected, the
 * company pages discovered for it, which member posts to which page, and the two
 * management actions (sync, disconnect).
 *
 * Shown only to a role that may manage it — the server answers anyone else
 * `403 {"error":"Admin or owner required"}` — and an organization with **no**
 * credential is rendered as its own ordinary state, not as a failure: that is
 * what almost every organization looks like.
 */
@Composable
private fun LinkedInSection(
    state: OrganizationDetailUiState,
    onAssignPage: (OrgMember, String?) -> Unit,
    onSync: () -> Unit,
    onDisconnect: () -> Unit,
) {
    var showDisconnectConfirm by remember { mutableStateOf(false) }
    val status = state.linkedIn

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(OrganizationDetailTestTags.LINKEDIN),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("LinkedIn company pages", style = MaterialTheme.typography.titleMedium)

            when {
                status == null && state.isLinkedInLoading ->
                    Text(
                        text = "Checking the LinkedIn connection…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                status?.connected == true -> ConnectedLinkedIn(
                    state = state,
                    status = status,
                    onAssignPage = onAssignPage,
                    onSync = onSync,
                    onDisconnect = { showDisconnectConfirm = true },
                )

                // No credential — the ordinary case, stated plainly.
                else -> Text(
                    text = LINKEDIN_NOT_CONNECTED_EXPLANATION,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(OrganizationDetailTestTags.LINKEDIN_NOT_CONNECTED),
                )
            }

            state.linkedInError?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag(OrganizationDetailTestTags.LINKEDIN_ERROR),
                )
            }
        }
    }

    if (showDisconnectConfirm) {
        AlertDialog(
            onDismissRequest = { showDisconnectConfirm = false },
            modifier = Modifier.testTag(OrganizationDetailTestTags.LINKEDIN_DISCONNECT_DIALOG),
            title = { Text("Disconnect LinkedIn?") },
            // The consequence is spelled out before anything is destroyed.
            text = { Text(LINKEDIN_DISCONNECT_CONSEQUENCE) },
            confirmButton = {
                TextButton(
                    onClick = { showDisconnectConfirm = false; onDisconnect() },
                    modifier = Modifier.testTag(OrganizationDetailTestTags.LINKEDIN_DISCONNECT_CONFIRM),
                ) { Text("Disconnect") }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

/** The connected half of [LinkedInSection]: pages, assignments and the actions. */
@Composable
private fun ConnectedLinkedIn(
    state: OrganizationDetailUiState,
    status: OrgLinkedInStatus,
    onAssignPage: (OrgMember, String?) -> Unit,
    onSync: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Text(
        text = status.expiresAt?.let { "Connected · access expires ${it.asDate()}" } ?: "Connected",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.testTag(OrganizationDetailTestTags.LINKEDIN_CONNECTED),
    )

    if (status.pages.isEmpty()) {
        Text(
            text = "No company pages yet. Sync to fetch the pages this credential administers.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(OrganizationDetailTestTags.LINKEDIN_NO_PAGES),
        )
    } else {
        status.pages.forEach { page ->
            Column(Modifier.testTag(OrganizationDetailTestTags.linkedInPage(page.id))) {
                Text(page.displayName, style = MaterialTheme.typography.bodyLarge)
                page.linkedInPageId?.let {
                    Text(
                        text = "LinkedIn page $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (state.members.isNotEmpty()) {
            HorizontalDivider()
            Text("Who posts to which page", style = MaterialTheme.typography.labelLarge)
            state.members.forEach { member ->
                LinkedInAssignmentRow(
                    member = member,
                    pages = status.pages,
                    assignedPage = status.pageFor(member.userId),
                    onAssign = { pageId -> onAssignPage(member, pageId) },
                )
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = onSync,
            enabled = !state.isLinkedInSyncing,
            modifier = Modifier.testTag(OrganizationDetailTestTags.LINKEDIN_SYNC),
        ) {
            Icon(Icons.Default.Sync, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (state.isLinkedInSyncing) "Syncing…" else "Sync pages")
        }
        TextButton(
            onClick = onDisconnect,
            modifier = Modifier.testTag(OrganizationDetailTestTags.LINKEDIN_DISCONNECT),
        ) {
            Icon(Icons.Default.LinkOff, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Disconnect")
        }
    }
}

/**
 * One member's page assignment. The API keeps at most one page per member
 * (`PUT …/linkedin/assignments` takes a single `{userId, pageId}` and clears the
 * assignment when `pageId` is absent), so this is a single-choice menu with an
 * explicit "Not assigned" entry rather than a multi-select.
 */
@Composable
private fun LinkedInAssignmentRow(
    member: OrgMember,
    pages: List<OrgLinkedInPage>,
    assignedPage: OrgLinkedInPage?,
    onAssign: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = member.label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box {
            TextButton(
                onClick = { expanded = true },
                modifier = Modifier.testTag(OrganizationDetailTestTags.linkedInAssignment(member.userId)),
            ) { Text(assignedPage?.displayName ?: "Not assigned") }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("Not assigned") },
                    onClick = { expanded = false; onAssign(null) },
                    modifier = Modifier.testTag(
                        OrganizationDetailTestTags.linkedInClearOption(member.userId),
                    ),
                )
                pages.forEach { page ->
                    DropdownMenuItem(
                        text = { Text(page.displayName) },
                        onClick = { expanded = false; onAssign(page.id) },
                        modifier = Modifier.testTag(
                            OrganizationDetailTestTags.linkedInPageOption(member.userId, page.id),
                        ),
                    )
                }
            }
        }
    }
}

/** ISO-8601 instants are shown as their date part; the time adds nothing here. */
private fun String.asDate(): String = substringBefore('T')

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

/**
 * Edits name, description and visibility. Visibility travels on the same
 * `PUT /api/organizations/{id}` as the rest, so
 * [com.interlinedlist.android.feature.organizations.domain.OrgPermissions.canEditVisibility]
 * matches `canEditOrganization` and this dialog only opens for roles that hold it.
 */
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
                    Column(Modifier.weight(1f)) {
                        Text("Public", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = if (isPublic) {
                                "Anyone can see and join."
                            } else {
                                "Invite-only; an owner or admin adds members."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = isPublic,
                        onCheckedChange = { isPublic = it },
                        modifier = Modifier.testTag(OrganizationDetailTestTags.EDIT_VISIBILITY),
                    )
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
private fun EmptyState(canAddMember: Boolean) {
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
                text = if (canAddMember) {
                    "Search above to add someone."
                } else {
                    "Only an owner or admin can add members."
                },
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
                organization = Organization("1", "Acme Corp", "We make everything", null, false, 3, OrgRole.OWNER, null),
                members = listOf(
                    OrgMember("u1", "ada", "Ada Lovelace", null, OrgRole.OWNER, active = true),
                    OrgMember("u3", "linus", null, null, OrgRole.OWNER, active = true),
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
            onJoin = {},
            onLeave = {},
            onAssignLinkedInPage = { _, _ -> },
            onSyncLinkedInPages = {},
            onRemoveLinkedInCredential = {},
        )
    }
}
