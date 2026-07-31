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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.interlinedlist.android.core.model.CustomerStatus
import com.interlinedlist.android.feature.profile.domain.FollowCounts
import com.interlinedlist.android.feature.profile.domain.FollowStatus
import com.interlinedlist.android.feature.profile.domain.MutualConnections
import com.interlinedlist.android.feature.profile.domain.ProfileUser
import com.interlinedlist.android.feature.profile.domain.PublicDocumentSummary
import com.interlinedlist.android.feature.profile.domain.PublicListSummary
import com.interlinedlist.android.feature.profile.domain.PublicPost
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
    modifier: Modifier = Modifier,
) {
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
    }
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
