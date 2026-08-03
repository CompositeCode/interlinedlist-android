package com.interlinedlist.android.feature.profile.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.core.model.CustomerStatus
import com.interlinedlist.android.feature.profile.domain.FollowCounts
import com.interlinedlist.android.feature.profile.domain.FollowStatus
import com.interlinedlist.android.feature.profile.domain.ModerationStatus
import com.interlinedlist.android.feature.profile.domain.MutualConnections
import com.interlinedlist.android.feature.profile.domain.ProfileUser
import com.interlinedlist.android.feature.profile.domain.PublicDocumentSummary
import com.interlinedlist.android.feature.profile.domain.PublicListSummary
import com.interlinedlist.android.feature.profile.domain.PublicPost
import com.interlinedlist.android.feature.profile.domain.ReportReason
import com.interlinedlist.android.feature.profile.ui.account.relativeTime

/** Stable test tags for the other-user profile's content tabs. */
object ProfileContentTestTags {
    const val TABS = "profileContentTabs"
    const val MUTUAL = "profileMutualConnections"
    const val CONTENT_PROGRESS = "profileContentProgress"
    const val CONTENT_ERROR = "profileContentError"
    const val CONTENT_EMPTY = "profileContentEmpty"
    const val CONTENT_LIST = "profileContentList"
    fun tab(tab: ProfileContentTab) = "profileTab_${tab.name}"
    fun postRow(id: String) = "profilePostRow_$id"
    fun listRow(id: String) = "profileListRow_$id"
    fun documentRow(id: String) = "profileDocumentRow_$id"
}

/** Stable test tags for the other-user profile's moderation overflow menu (Milestone D). */
object ProfileModerationTestTags {
    const val OVERFLOW = "profileModerationOverflow"
    const val MENU = "profileModerationMenu"
    const val MENU_BLOCK = "profileModerationMenuBlock"
    const val MENU_MUTE = "profileModerationMenuMute"
    const val MENU_REPORT = "profileModerationMenuReport"
    const val BLOCK_DIALOG = "profileModerationBlockDialog"
    const val BLOCK_CONFIRM = "profileModerationBlockConfirm"
    const val MUTE_DIALOG = "profileModerationMuteDialog"
    const val MUTE_CONFIRM = "profileModerationMuteConfirm"
    const val REPORT_DIALOG = "profileModerationReportDialog"
    const val REPORT_CONFIRM = "profileModerationReportConfirm"
    const val REPORT_DETAIL = "profileModerationReportDetail"
    const val REPORT_SUBMITTED = "profileModerationReportSubmitted"
    fun reportReason(reason: ReportReason) = "profileModerationReason_${reason.name}"
}

/**
 * Another user's public profile, reached by drilling down from search (route
 * `profile/{username}`). Shows the profile header, a mutual-connections indicator,
 * and content tabs (Posts / Lists / Documents), each backed by its own endpoint.
 *
 * @param onBack pop back to the previous screen (search).
 * @param onOpenFollowers open this user's followers list (`followers/{username}`).
 * @param onOpenFollowing open this user's following list (`following/{username}`).
 * @param onOpenList open a public list read-only (`publicList/{username}/{listId}`).
 * @param onOpenDocument open a public document read-only (`publicDocument/{documentId}`).
 */
@Composable
fun UserProfileRoute(
    onBack: () -> Unit,
    onOpenFollowers: (String) -> Unit,
    onOpenFollowing: (String) -> Unit,
    // Read-only content drill-downs (Milestone L). Defaulted to no-ops so existing app
    // wiring compiles unchanged; wire these to the `publicList`/`publicDocument` routes
    // to enable opening public content (see the module's nav-wiring snippet).
    onOpenList: (String, String) -> Unit = { _, _ -> },
    onOpenDocument: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: UserProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    UserProfileScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::refresh,
        onToggleFollow = viewModel::toggleFollow,
        onSelectTab = viewModel::selectTab,
        onOpenFollowers = { state.user?.username?.let(onOpenFollowers) },
        onOpenFollowing = { state.user?.username?.let(onOpenFollowing) },
        onOpenList = { listId -> state.user?.username?.let { onOpenList(it, listId) } },
        onOpenDocument = onOpenDocument,
        onToggleBlock = viewModel::toggleBlock,
        onToggleMute = viewModel::toggleMute,
        onReport = viewModel::report,
        onAcknowledgeReport = viewModel::acknowledgeReport,
        modifier = modifier,
    )
}

/** Stateless other-user profile UI with content tabs. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    state: ProfileUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onToggleFollow: () -> Unit = {},
    onSelectTab: (ProfileContentTab) -> Unit = {},
    onOpenFollowers: () -> Unit = {},
    onOpenFollowing: () -> Unit = {},
    onOpenList: (String) -> Unit = {},
    onOpenDocument: (String) -> Unit = {},
    onToggleBlock: () -> Unit = {},
    onToggleMute: () -> Unit = {},
    onReport: (ReportReason, String?) -> Unit = { _, _ -> },
    onAcknowledgeReport: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Which moderation dialog (if any) is currently up.
    var moderationDialog by remember { mutableStateOf(ModerationDialog.NONE) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(state.user?.displayLabel ?: "Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(ProfileTestTags.BACK)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.canModerate) {
                        ModerationOverflowMenu(
                            status = state.moderationStatus,
                            enabled = !state.isModerationActionInProgress,
                            onBlock = { moderationDialog = ModerationDialog.BLOCK },
                            onMute = { moderationDialog = ModerationDialog.MUTE },
                            onReport = { moderationDialog = ModerationDialog.REPORT },
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.user != null -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .testTag(ProfileContentTestTags.CONTENT_LIST),
            ) {
                item(key = "header") {
                    ProfileContent(
                        user = state.user,
                        counts = state.followCounts,
                        onOpenFollowers = onOpenFollowers,
                        onOpenFollowing = onOpenFollowing,
                        followStatus = state.followStatus,
                        isFollowActionInProgress = state.isFollowActionInProgress,
                        onToggleFollow = onToggleFollow,
                    )
                }

                state.mutualConnections?.takeIf { it.total > 0 }?.let { mutual ->
                    item(key = "mutual") { MutualConnectionsRow(mutual) }
                }

                item(key = "tabs") {
                    ContentTabRow(selected = state.selectedTab, onSelectTab = onSelectTab)
                }

                contentTabItems(
                    state = state,
                    onOpenList = onOpenList,
                    onOpenDocument = onOpenDocument,
                    onRetry = onRetry,
                )
            }

            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(Modifier.testTag(ProfileTestTags.PROGRESS))
            }

            else -> Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.errorMessage ?: "Couldn't load this profile.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag(ProfileTestTags.ERROR),
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRetry) { Text("Retry") }
                }
            }
        }

        val label = state.user?.displayLabel ?: "this user"
        when (moderationDialog) {
            ModerationDialog.BLOCK -> ModerationConfirmDialog(
                title = if (state.moderationStatus.isBlocked) "Unblock $label?" else "Block $label?",
                body = if (state.moderationStatus.isBlocked) {
                    "They'll be able to see your profile and interact with you again."
                } else {
                    "They won't be able to see your profile or interact with you."
                },
                confirmLabel = if (state.moderationStatus.isBlocked) "Unblock" else "Block",
                dialogTag = ProfileModerationTestTags.BLOCK_DIALOG,
                confirmTag = ProfileModerationTestTags.BLOCK_CONFIRM,
                onConfirm = {
                    onToggleBlock()
                    moderationDialog = ModerationDialog.NONE
                },
                onDismiss = { moderationDialog = ModerationDialog.NONE },
            )

            ModerationDialog.MUTE -> ModerationConfirmDialog(
                title = if (state.moderationStatus.isMuted) "Unmute $label?" else "Mute $label?",
                body = if (state.moderationStatus.isMuted) {
                    "You'll start seeing their activity again."
                } else {
                    "You won't see their activity, but they won't be notified."
                },
                confirmLabel = if (state.moderationStatus.isMuted) "Unmute" else "Mute",
                dialogTag = ProfileModerationTestTags.MUTE_DIALOG,
                confirmTag = ProfileModerationTestTags.MUTE_CONFIRM,
                onConfirm = {
                    onToggleMute()
                    moderationDialog = ModerationDialog.NONE
                },
                onDismiss = { moderationDialog = ModerationDialog.NONE },
            )

            ModerationDialog.REPORT -> ReportDialog(
                targetLabel = label,
                onSubmit = { reason, detail ->
                    onReport(reason, detail)
                    moderationDialog = ModerationDialog.NONE
                },
                onDismiss = { moderationDialog = ModerationDialog.NONE },
            )

            ModerationDialog.NONE -> Unit
        }

        if (state.reportSubmitted) {
            ReportSubmittedDialog(onDismiss = onAcknowledgeReport)
        }
    }
}

/** Which moderation dialog is showing over the profile. */
private enum class ModerationDialog { NONE, BLOCK, MUTE, REPORT }

/** The three-dot overflow menu offering Block / Mute / Report on another user's profile. */
@Composable
private fun ModerationOverflowMenu(
    status: ModerationStatus,
    enabled: Boolean,
    onBlock: () -> Unit,
    onMute: () -> Unit,
    onReport: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.wrapContentSize(Alignment.TopEnd)) {
        IconButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.testTag(ProfileModerationTestTags.OVERFLOW),
        ) {
            Icon(Icons.Default.MoreVert, contentDescription = "More options")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.testTag(ProfileModerationTestTags.MENU),
        ) {
            DropdownMenuItem(
                text = { Text(if (status.isBlocked) "Unblock" else "Block") },
                leadingIcon = { Icon(Icons.Default.Block, contentDescription = null) },
                onClick = {
                    expanded = false
                    onBlock()
                },
                modifier = Modifier.testTag(ProfileModerationTestTags.MENU_BLOCK),
            )
            DropdownMenuItem(
                text = { Text(if (status.isMuted) "Unmute" else "Mute") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.VolumeOff, contentDescription = null) },
                onClick = {
                    expanded = false
                    onMute()
                },
                modifier = Modifier.testTag(ProfileModerationTestTags.MENU_MUTE),
            )
            DropdownMenuItem(
                text = { Text("Report") },
                leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null) },
                onClick = {
                    expanded = false
                    onReport()
                },
                modifier = Modifier.testTag(ProfileModerationTestTags.MENU_REPORT),
            )
        }
    }
}

/** A generic confirm dialog for the destructive block/mute toggles. */
@Composable
private fun ModerationConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    dialogTag: String,
    confirmTag: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(dialogTag),
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag(confirmTag)) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** The report dialog: pick a reason (required) and add optional detail. */
@Composable
private fun ReportDialog(
    targetLabel: String,
    onSubmit: (ReportReason, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedReason by remember { mutableStateOf<ReportReason?>(null) }
    var detail by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(ProfileModerationTestTags.REPORT_DIALOG),
        title = { Text("Report $targetLabel") },
        text = {
            Column {
                Text(
                    text = "Why are you reporting this user?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                ReportReason.entries.forEach { reason ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedReason == reason,
                                onClick = { selectedReason = reason },
                            )
                            .testTag(ProfileModerationTestTags.reportReason(reason))
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedReason == reason,
                            onClick = { selectedReason = reason },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(reason.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = detail,
                    onValueChange = { detail = it },
                    label = { Text("Add detail (optional)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(ProfileModerationTestTags.REPORT_DETAIL),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selectedReason?.let { onSubmit(it, detail.ifBlank { null }) } },
                enabled = selectedReason != null,
                modifier = Modifier.testTag(ProfileModerationTestTags.REPORT_CONFIRM),
            ) {
                Text("Submit report")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** A brief confirmation shown after a report is successfully submitted. */
@Composable
private fun ReportSubmittedDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(ProfileModerationTestTags.REPORT_SUBMITTED),
        title = { Text("Report submitted") },
        text = { Text("Thanks — our team will review this report.") },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}

/** Renders the selected tab's rows as LazyColumn items (loading / error / empty / content). */
private fun androidx.compose.foundation.lazy.LazyListScope.contentTabItems(
    state: ProfileUiState,
    onOpenList: (String) -> Unit,
    onOpenDocument: (String) -> Unit,
    onRetry: () -> Unit,
) {
    val content = state.content
    when {
        content.isLoading -> item(key = "content-loading") {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.testTag(ProfileContentTestTags.CONTENT_PROGRESS))
            }
        }

        content.errorMessage != null -> item(key = "content-error") {
            Column(
                Modifier.fillMaxWidth().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = content.errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(ProfileContentTestTags.CONTENT_ERROR),
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRetry) { Text("Retry") }
            }
        }

        else -> when (state.selectedTab) {
            ProfileContentTab.POSTS -> postItems(content.posts)
            ProfileContentTab.LISTS -> listItems(content.lists, onOpenList)
            ProfileContentTab.DOCUMENTS -> documentItems(content.documents, onOpenDocument)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.postItems(posts: List<PublicPost>) {
    if (posts.isEmpty()) {
        emptyItem("No posts yet.")
        return
    }
    items(posts, key = { it.id }) { post -> PostCard(post) }
}

private fun androidx.compose.foundation.lazy.LazyListScope.listItems(
    lists: List<PublicListSummary>,
    onOpenList: (String) -> Unit,
) {
    if (lists.isEmpty()) {
        emptyItem("No public lists yet.")
        return
    }
    items(lists, key = { it.id }) { list ->
        SummaryRow(
            title = list.displayTitle,
            subtitle = list.description,
            tag = ProfileContentTestTags.listRow(list.id),
            onClick = { onOpenList(list.id) },
        )
        HorizontalDivider()
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.documentItems(
    documents: List<PublicDocumentSummary>,
    onOpenDocument: (String) -> Unit,
) {
    if (documents.isEmpty()) {
        emptyItem("No public documents yet.")
        return
    }
    items(documents, key = { it.id }) { document ->
        SummaryRow(
            title = document.displayTitle,
            subtitle = null,
            tag = ProfileContentTestTags.documentRow(document.id),
            onClick = { onOpenDocument(document.id) },
        )
        HorizontalDivider()
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.emptyItem(message: String) {
    item(key = "content-empty") {
        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(ProfileContentTestTags.CONTENT_EMPTY),
            )
        }
    }
}

@Composable
private fun MutualConnectionsRow(mutual: MutualConnections) {
    val label = if (mutual.total == 1) {
        "1 mutual connection"
    } else {
        "${mutual.total} mutual connections"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp)
            .testTag(ProfileContentTestTags.MUTUAL),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Group,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ContentTabRow(
    selected: ProfileContentTab,
    onSelectTab: (ProfileContentTab) -> Unit,
) {
    val tabs = ProfileContentTab.entries
    TabRow(
        selectedTabIndex = tabs.indexOf(selected),
        modifier = Modifier.fillMaxWidth().testTag(ProfileContentTestTags.TABS),
    ) {
        tabs.forEach { tab ->
            Tab(
                selected = tab == selected,
                onClick = { onSelectTab(tab) },
                modifier = Modifier.testTag(ProfileContentTestTags.tab(tab)),
                text = { Text(tab.label) },
            )
        }
    }
}

@Composable
private fun PostCard(post: PublicPost) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag(ProfileContentTestTags.postRow(post.id)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = post.content,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            post.createdAt?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = relativeTime(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SummaryRow(
    title: String,
    subtitle: String?,
    tag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(tag)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Human-readable label for a content tab. */
private val ProfileContentTab.label: String
    get() = when (this) {
        ProfileContentTab.POSTS -> "Posts"
        ProfileContentTab.LISTS -> "Lists"
        ProfileContentTab.DOCUMENTS -> "Documents"
    }

@Preview(showBackground = true)
@Composable
private fun UserProfileScreenPreview() {
    InterlinedListTheme {
        UserProfileScreen(
            state = ProfileUiState(
                user = ProfileUser(
                    id = "2",
                    username = "ada",
                    displayName = "Ada Lovelace",
                    avatarUrl = null,
                    bio = "First programmer.",
                    customerStatus = CustomerStatus.FREE,
                    isCurrentUser = false,
                ),
                isLoading = false,
                followStatus = FollowStatus.NOT_FOLLOWING,
                followCounts = FollowCounts(followers = 128, following = 87),
                mutualConnections = MutualConnections(mutualFollowers = 3, mutualFollowing = 1),
                content = PublicContentState(
                    posts = listOf(PublicPost("m1", "Hello from Ada!", null)),
                    isLoading = false,
                    loadedTabs = setOf(ProfileContentTab.POSTS),
                ),
            ),
            onBack = {},
            onRetry = {},
        )
    }
}
