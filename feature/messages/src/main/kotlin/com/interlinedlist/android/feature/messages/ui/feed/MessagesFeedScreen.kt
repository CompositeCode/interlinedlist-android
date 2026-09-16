package com.interlinedlist.android.feature.messages.ui.feed

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.core.model.ViewingPreference
import com.interlinedlist.android.feature.messages.domain.CrossPostStatus
import com.interlinedlist.android.feature.messages.domain.LinkedNetwork
import com.interlinedlist.android.feature.messages.domain.Message
import com.interlinedlist.android.feature.messages.domain.MessageVisibility
import com.interlinedlist.android.feature.messages.domain.ReportReason
import com.interlinedlist.android.feature.messages.domain.TagSuggestion
import com.interlinedlist.android.feature.messages.ui.components.EditMessageSheet
import com.interlinedlist.android.feature.messages.ui.components.MessageCard
import com.interlinedlist.android.feature.messages.ui.components.ModerationDialog
import com.interlinedlist.android.feature.messages.ui.components.ReportDialog
import com.interlinedlist.android.feature.messages.ui.readMediaBytes
import com.interlinedlist.android.feature.messages.ui.trending.TrendingTagsRail
import com.interlinedlist.android.feature.messages.ui.trending.TrendingTagsUiState
import com.interlinedlist.android.feature.messages.ui.trending.TrendingTagsViewModel
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Stable test tags for the feed screen. */
object MessagesFeedTags {
    const val LIST = "messagesFeedList"
    const val EMPTY = "messagesFeedEmpty"
    const val ERROR = "messagesFeedError"
    const val LOCKED = "messagesFeedLocked"
    const val PROGRESS = "messagesFeedProgress"
    const val FAB = "messagesFeedFab"
    const val COMPOSE_INPUT = "messagesComposeInput"
    const val COMPOSE_SUBMIT = "messagesComposeSubmit"
    const val COMPOSE_ADD_IMAGE = "messagesComposeAddImage"
    const val COMPOSE_ADD_VIDEO = "messagesComposeAddVideo"
    const val COMPOSE_SCHEDULE = "messagesComposeSchedule"
    const val SCHEDULED_ACTION = "messagesFeedScheduledAction"

    /** The in-feed All / My / Following / Followers switcher. */
    const val VIEW_PREFERENCES = "messagesFeedViewPreferences"
    /** Prefix for one switcher chip; suffixed with the preference's wire value. */
    const val VIEW_PREFERENCE_PREFIX = "messagesFeedViewPreference_"

    /** The always-on InterlinedList destination chip. */
    const val DESTINATION_IL = "messagesComposeDestinationInterlinedList"
    /** Prefix for a per-network destination chip; suffixed with the network id. */
    const val DESTINATION_PREFIX = "messagesComposeDestination_"
    /** Hint shown when the account has no linked networks to cross-post to. */
    const val DESTINATIONS_HINT = "messagesComposeDestinationsHint"
    /** Post-send banner listing per-network cross-post statuses. */
    const val CROSS_POST_STATUS = "messagesFeedCrossPostStatus"

    /** Composer visibility chips and the private-selection hint. */
    const val VISIBILITY_PUBLIC = "messagesComposeVisibilityPublic"
    const val VISIBILITY_PRIVATE = "messagesComposeVisibilityPrivate"
    const val VISIBILITY_HINT = "messagesComposeVisibilityHint"

    /** The composer's tag field, its Add action, and the committed tag chips. */
    const val COMPOSE_TAG_INPUT = "messagesComposeTagInput"
    const val COMPOSE_TAG_ADD = "messagesComposeTagAdd"
    const val COMPOSE_TAGS = "messagesComposeTags"
    /** Prefix for a committed tag chip; suffixed with the tag. */
    const val COMPOSE_TAG_PREFIX = "messagesComposeTag_"
    /** The autocomplete suggestion row, and one suggestion chip within it. */
    const val TAG_SUGGESTIONS = "messagesComposeTagSuggestions"
    const val TAG_SUGGESTION_PREFIX = "messagesComposeTagSuggestion_"

    /** Back arrow shown instead of the tab bar when the feed is filtered to a tag. */
    const val BACK = "messagesFeedBack"
    /** Title shown while the feed is filtered to a tag. */
    const val TAG_TITLE = "messagesFeedTagTitle"

    /** The quoted message attached to the composer, and its always-public banner. */
    const val QUOTE_ATTACHED = "messagesComposeQuoteAttached"
    const val QUOTE_PUBLIC_BANNER = "messagesComposeQuoteBanner"

    fun destinationTag(networkId: String): String = DESTINATION_PREFIX + networkId

    fun composeTagTag(tag: String): String = COMPOSE_TAG_PREFIX + tag

    fun tagSuggestionTag(tag: String): String = TAG_SUGGESTION_PREFIX + tag

    fun viewPreferenceTag(preference: ViewingPreference): String =
        VIEW_PREFERENCE_PREFIX + preference.wire
}

/**
 * Hilt-wired feed entry point, hosted twice: as the Messages tab, and as the
 * tag-filtered feed (`MessagesDestinations.TAG_FEED`). Which one it is comes from
 * the ViewModel's nav arguments, so both get identical paging, view-preference and
 * moderation behaviour from the same code.
 *
 * @param onOpenMessage navigates to the detail screen for the given message id.
 * @param onOpenScheduled navigates to the Scheduled messages screen.
 * @param onOpenTag opens the feed filtered to a tapped tag.
 * @param onBack pops the tag feed; ignored on the tab root, which has no back arrow.
 */
@Composable
fun MessagesRoute(
    onOpenMessage: (String) -> Unit,
    onOpenScheduled: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenTag: ((String) -> Unit)? = null,
    onBack: () -> Unit = {},
    viewModel: MessagesFeedViewModel = hiltViewModel(),
    trendingViewModel: TrendingTagsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Trending has its own ViewModel: it outlives a feed refresh, and a trending
    // lookup that fails must not read as a feed that failed.
    val trending by trendingViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    MessagesFeedScreen(
        state = state,
        trending = trending,
        onRetryTrending = trendingViewModel::refresh,
        onRefresh = viewModel::refresh,
        onLoadMore = viewModel::loadMore,
        onViewingPreferenceChange = viewModel::onViewingPreferenceChange,
        onOpenMessage = onOpenMessage,
        onOpenScheduled = onOpenScheduled,
        onOpenTag = onOpenTag,
        onBack = onBack,
        onDig = viewModel::onDig,
        onDelete = viewModel::onDelete,
        onPush = viewModel::onPush,
        onQuote = viewModel::openQuote,
        onReport = viewModel::openReport,
        onEdit = viewModel::openEdit,
        onBlockUser = { viewModel.openModeration(it, ModerationAction.BLOCK) },
        onMuteUser = { viewModel.openModeration(it, ModerationAction.MUTE) },
        onReportUser = { viewModel.openModeration(it, ModerationAction.REPORT) },
        onFetchMetadata = viewModel::onFetchMetadata,
        onOpenCompose = viewModel::openCompose,
        onDismissCompose = viewModel::dismissCompose,
        onComposeTextChange = viewModel::onComposeTextChange,
        onPost = viewModel::post,
        onAttachMedia = { uri, isVideo ->
            // Read the picked media at the UI layer; the ViewModel stays URI-free.
            val media = readMediaBytes(context, uri, isVideo)
            if (media != null) {
                viewModel.onAttachMedia(media.bytes, media.fileName, media.mimeType, isVideo)
            }
        },
        onRemoveAttachment = viewModel::onRemoveAttachment,
        onTagQueryChange = viewModel::onTagQueryChange,
        onCommitTag = viewModel::commitTag,
        onSelectTagSuggestion = viewModel::onSelectTagSuggestion,
        onRemoveTag = viewModel::onRemoveTag,
        onScheduleChange = viewModel::onScheduleChange,
        onVisibilityChange = viewModel::onVisibilityChange,
        onToggleNetwork = viewModel::onToggleNetwork,
        onDismissCrossPostStatuses = viewModel::dismissCrossPostStatuses,
        onDismissReport = viewModel::dismissReport,
        onSubmitReport = viewModel::submitReport,
        onEditTextChange = viewModel::onEditTextChange,
        onDismissEdit = viewModel::dismissEdit,
        onSaveEdit = viewModel::saveEdit,
        onDismissModeration = viewModel::dismissModeration,
        onConfirmModeration = viewModel::confirmModeration,
        modifier = modifier,
    )
}

/** Stateless feed UI — drives all list/empty/error/locked states from [state]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesFeedScreen(
    state: MessagesFeedUiState,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenMessage: (String) -> Unit,
    onDig: (Message) -> Unit,
    onDelete: (Message) -> Unit,
    onOpenCompose: () -> Unit,
    onDismissCompose: () -> Unit,
    onComposeTextChange: (String) -> Unit,
    onPost: () -> Unit,
    modifier: Modifier = Modifier,
    trending: TrendingTagsUiState = TrendingTagsUiState(),
    onRetryTrending: () -> Unit = {},
    onViewingPreferenceChange: (ViewingPreference) -> Unit = {},
    onOpenScheduled: () -> Unit = {},
    onOpenTag: ((String) -> Unit)? = null,
    onBack: () -> Unit = {},
    onPush: (Message) -> Unit = {},
    onQuote: (Message) -> Unit = {},
    onReport: (Message) -> Unit = {},
    onEdit: (Message) -> Unit = {},
    onBlockUser: (Message) -> Unit = {},
    onMuteUser: (Message) -> Unit = {},
    onReportUser: (Message) -> Unit = {},
    onFetchMetadata: (Message) -> Unit = {},
    onAttachMedia: (Uri, Boolean) -> Unit = { _, _ -> },
    onRemoveAttachment: (PendingAttachment) -> Unit = {},
    onTagQueryChange: (String) -> Unit = {},
    onCommitTag: () -> Unit = {},
    onSelectTagSuggestion: (TagSuggestion) -> Unit = {},
    onRemoveTag: (String) -> Unit = {},
    onScheduleChange: (String?) -> Unit = {},
    onVisibilityChange: (MessageVisibility) -> Unit = {},
    onToggleNetwork: (String) -> Unit = {},
    onDismissCrossPostStatuses: () -> Unit = {},
    onDismissReport: () -> Unit = {},
    onSubmitReport: (ReportReason, String) -> Unit = { _, _ -> },
    onEditTextChange: (String) -> Unit = {},
    onDismissEdit: () -> Unit = {},
    onSaveEdit: () -> Unit = {},
    onDismissModeration: () -> Unit = {},
    onConfirmModeration: (ReportReason?, String) -> Unit = { _, _ -> },
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    if (state.tag != null) {
                        Text(
                            text = state.tag,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag(MessagesFeedTags.TAG_TITLE),
                        )
                    } else {
                        Text("Messages")
                    }
                },
                navigationIcon = {
                    // The tag feed is pushed on top of a tab, so it carries its own
                    // back affordance; the tab root does not.
                    if (state.isTagFeed) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag(MessagesFeedTags.BACK),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    }
                },
                actions = {
                    if (!state.isTagFeed) {
                        IconButton(
                            onClick = onOpenScheduled,
                            modifier = Modifier.testTag(MessagesFeedTags.SCHEDULED_ACTION),
                        ) {
                            Icon(Icons.Filled.Schedule, contentDescription = "Scheduled messages")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            // Composing from a tag feed would post an untagged message into a feed
            // it cannot appear in, so the composer stays on the main feed.
            if (!state.subscriptionRequired && !state.isTagFeed) {
                FloatingActionButton(
                    onClick = onOpenCompose,
                    modifier = Modifier.testTag(MessagesFeedTags.FAB),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "New message")
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ViewPreferenceSwitcher(
                selected = state.viewingPreference,
                enabled = !state.isChangingViewingPreference,
                onSelect = onViewingPreferenceChange,
            )
            // A rail of doors that open nothing is not worth its space, so it
            // only exists where the host wired somewhere to go.
            val trendingRail: (@Composable () -> Unit)? = onOpenTag?.let { openTag ->
                {
                    TrendingTagsRail(
                        state = trending,
                        onOpenTag = openTag,
                        onRetry = onRetryTrending,
                    )
                }
            }
            when {
                state.subscriptionRequired -> LockedState(message = state.errorMessage)
                else -> FeedContent(
                    state = state,
                    trendingRail = trendingRail,
                    onRefresh = onRefresh,
                    onLoadMore = onLoadMore,
                    onOpenMessage = onOpenMessage,
                    onOpenTag = onOpenTag,
                    onDig = onDig,
                    onDelete = onDelete,
                    onPush = onPush,
                    onQuote = onQuote,
                    onReport = onReport,
                    onEdit = onEdit,
                    onBlockUser = onBlockUser,
                    onMuteUser = onMuteUser,
                    onReportUser = onReportUser,
                    onFetchMetadata = onFetchMetadata,
                )
            }
        }
    }

    if (state.isComposeOpen) {
        ComposeSheet(
            state = state,
            onTextChange = onComposeTextChange,
            onDismiss = onDismissCompose,
            onPost = onPost,
            onAttachMedia = onAttachMedia,
            onRemoveAttachment = onRemoveAttachment,
            onTagQueryChange = onTagQueryChange,
            onCommitTag = onCommitTag,
            onSelectTagSuggestion = onSelectTagSuggestion,
            onRemoveTag = onRemoveTag,
            onScheduleChange = onScheduleChange,
            onVisibilityChange = onVisibilityChange,
            onToggleNetwork = onToggleNetwork,
        )
    }

    if (state.crossPostStatuses.isNotEmpty()) {
        CrossPostStatusBanner(
            statuses = state.crossPostStatuses,
            onDismiss = onDismissCrossPostStatuses,
        )
    }

    state.reportTarget?.let {
        ReportDialog(
            onDismiss = onDismissReport,
            onSubmit = onSubmitReport,
            isSubmitting = state.isReporting,
        )
    }

    if (state.editTarget != null) {
        EditMessageSheet(
            text = state.editText,
            canSave = state.canSaveEdit,
            isSaving = state.isSavingEdit,
            onTextChange = onEditTextChange,
            onDismiss = onDismissEdit,
            onSave = onSaveEdit,
        )
    }

    state.moderationTarget?.let { target ->
        ModerationDialog(
            target = target,
            isSubmitting = state.isModerating,
            onDismiss = onDismissModeration,
            onConfirm = onConfirmModeration,
        )
    }
}

/**
 * The in-feed view switcher: the same four choices as Settings -> View Preferences
 * on the web. Selecting one saves it to the account (so the web agrees) and reloads
 * the feed, which is why the row is disabled while a save is in flight.
 */
@Composable
private fun ViewPreferenceSwitcher(
    selected: ViewingPreference,
    enabled: Boolean,
    onSelect: (ViewingPreference) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag(MessagesFeedTags.VIEW_PREFERENCES),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ViewingPreference.entries.forEach { preference ->
            FilterChip(
                selected = preference == selected,
                onClick = { onSelect(preference) },
                enabled = enabled,
                label = { Text(preference.label) },
                modifier = Modifier.testTag(MessagesFeedTags.viewPreferenceTag(preference)),
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** The switcher wording, matching Settings -> View Preferences on the web. */
private val ViewingPreference.label: String
    get() = when (this) {
        ViewingPreference.ALL -> "All Messages"
        ViewingPreference.MINE -> "My Messages"
        ViewingPreference.FOLLOWING -> "Following Only"
        ViewingPreference.FOLLOWERS -> "Followers Only"
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedContent(
    state: MessagesFeedUiState,
    trendingRail: (@Composable () -> Unit)?,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenMessage: (String) -> Unit,
    onOpenTag: ((String) -> Unit)?,
    onDig: (Message) -> Unit,
    onDelete: (Message) -> Unit,
    onPush: (Message) -> Unit,
    onQuote: (Message) -> Unit,
    onReport: (Message) -> Unit,
    onEdit: (Message) -> Unit,
    onBlockUser: (Message) -> Unit,
    onMuteUser: (Message) -> Unit,
    onReportUser: (Message) -> Unit,
    onFetchMetadata: (Message) -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            state.isEmpty && state.isRefreshing -> LoadingState()
            state.isEmpty && state.errorMessage != null -> ErrorState(state.errorMessage, onRefresh)
            state.isEmpty -> EmptyState(tag = state.tag, trendingRail = trendingRail)
            else -> FeedList(
                state = state,
                trendingRail = trendingRail,
                onLoadMore = onLoadMore,
                onOpenMessage = onOpenMessage,
                onOpenTag = onOpenTag,
                onDig = onDig,
                onDelete = onDelete,
                onPush = onPush,
                onQuote = onQuote,
                onReport = onReport,
                onEdit = onEdit,
                onBlockUser = onBlockUser,
                onMuteUser = onMuteUser,
                onReportUser = onReportUser,
                onFetchMetadata = onFetchMetadata,
            )
        }
    }
}

@Composable
private fun FeedList(
    state: MessagesFeedUiState,
    trendingRail: (@Composable () -> Unit)?,
    onLoadMore: () -> Unit,
    onOpenMessage: (String) -> Unit,
    onOpenTag: ((String) -> Unit)?,
    onDig: (Message) -> Unit,
    onDelete: (Message) -> Unit,
    onPush: (Message) -> Unit,
    onQuote: (Message) -> Unit,
    onReport: (Message) -> Unit,
    onEdit: (Message) -> Unit,
    onBlockUser: (Message) -> Unit,
    onMuteUser: (Message) -> Unit,
    onReportUser: (Message) -> Unit,
    onFetchMetadata: (Message) -> Unit,
) {
    val listState = rememberLazyListState()
    // Trigger load-more when the last item scrolls into view.
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            state.canLoadMore && !state.isLoadingMore && last >= state.messages.size - 3
        }
    }
    if (shouldLoadMore) onLoadMore()

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .testTag(MessagesFeedTags.LIST),
    ) {
        // First row of the feed rather than a fixed band above it: the switcher
        // and the composer already own the top of this screen, so the rail earns
        // its place by scrolling away once the reader is past it.
        trendingRail?.let { rail -> item(key = "trendingTags") { rail() } }
        items(state.messages, key = { it.id }) { message ->
            MessageCard(
                message = message,
                onClick = { onOpenMessage(message.id) },
                onDig = { onDig(message) },
                onDelete = { onDelete(message) },
                onReport = { onReport(message) },
                onEdit = { onEdit(message) },
                onBlockUser = { onBlockUser(message) },
                onMuteUser = { onMuteUser(message) },
                onReportUser = { onReportUser(message) },
                onOpenLink = { onFetchMetadata(message) },
                onPush = { onPush(message) },
                onQuote = { onQuote(message) },
                // The embedded original opens on its own page.
                onOpenPushedMessage = onOpenMessage,
                onTagClick = onOpenTag,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        if (state.isLoadingMore) {
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.testTag(MessagesFeedTags.PROGRESS))
    }
}

@Composable
private fun EmptyState(
    tag: String? = null,
    trendingRail: (@Composable () -> Unit)? = null,
) {
    Column(Modifier.fillMaxSize()) {
        // An empty feed is exactly where somewhere-to-go matters most, so the
        // rail stays on screen instead of scrolling with a list that has no rows.
        trendingRail?.invoke()
        Box(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                // The tag feed hides the composer, so "be the first to post"
                // would be an invitation the screen cannot honour.
                text = if (tag != null) {
                    "Nothing tagged \u201C$tag\u201D yet."
                } else {
                    "No messages yet. Be the first to post."
                },
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(MessagesFeedTags.EMPTY),
            )
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag(MessagesFeedTags.ERROR),
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun LockedState(message: String?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Subscribers only",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message ?: "Upgrade to an active subscription to view the message feed.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(MessagesFeedTags.LOCKED),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposeSheet(
    state: MessagesFeedUiState,
    onTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onPost: () -> Unit,
    onAttachMedia: (Uri, Boolean) -> Unit,
    onRemoveAttachment: (PendingAttachment) -> Unit,
    onTagQueryChange: (String) -> Unit,
    onCommitTag: () -> Unit,
    onSelectTagSuggestion: (TagSuggestion) -> Unit,
    onRemoveTag: (String) -> Unit,
    onScheduleChange: (String?) -> Unit,
    onVisibilityChange: (MessageVisibility) -> Unit,
    onToggleNetwork: (String) -> Unit,
) {
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri -> uri?.let { onAttachMedia(it, false) } }
    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri -> uri?.let { onAttachMedia(it, true) } }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text(
                text = when {
                    state.isQuoting -> "Quote message"
                    state.isScheduled -> "Schedule message"
                    else -> "New message"
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.composeText,
                onValueChange = onTextChange,
                placeholder = {
                    Text(if (state.isQuoting) "Add a comment" else "What's on your mind?")
                },
                enabled = !state.isPosting,
                minLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(MessagesFeedTags.COMPOSE_INPUT),
            )

            state.quoteTarget?.let { quoted ->
                Spacer(Modifier.height(8.dp))
                QuotedMessage(quoted)
                Spacer(Modifier.height(8.dp))
                AlwaysPublicBanner()
            }

            if (state.hasAttachments) {
                Spacer(Modifier.height(8.dp))
                AttachmentRow(state.attachments, onRemoveAttachment)
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = { imagePicker.launch("image/*") },
                    enabled = !state.isPosting,
                    modifier = Modifier.testTag(MessagesFeedTags.COMPOSE_ADD_IMAGE),
                ) {
                    Icon(Icons.Filled.Image, contentDescription = "Attach image")
                }
                IconButton(
                    onClick = { videoPicker.launch("video/*") },
                    enabled = !state.isPosting,
                    modifier = Modifier.testTag(MessagesFeedTags.COMPOSE_ADD_VIDEO),
                ) {
                    Icon(Icons.Filled.Videocam, contentDescription = "Attach video")
                }
                // Scheduling and pushedMessageId are mutually exclusive on the
                // create endpoint, so a quote is not offered a send time.
                if (!state.isQuoting) {
                    ScheduleChip(
                        scheduledAt = state.scheduledAt,
                        enabled = !state.isPosting,
                        onSchedule = onScheduleChange,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            TagInput(
                tags = state.composeTags,
                query = state.tagQuery,
                suggestions = state.tagSuggestions,
                canCommit = state.canCommitTag,
                isLoadingSuggestions = state.isLoadingTagSuggestions,
                enabled = !state.isPosting,
                onQueryChange = onTagQueryChange,
                onCommit = onCommitTag,
                onSelectSuggestion = onSelectTagSuggestion,
                onRemoveTag = onRemoveTag,
            )

            Spacer(Modifier.height(12.dp))
            VisibilityRow(
                visibility = state.composeVisibility,
                enabled = !state.isPosting,
                canChangeVisibility = state.canChangeVisibility,
                onVisibilityChange = onVisibilityChange,
            )

            Spacer(Modifier.height(12.dp))
            DestinationsRow(
                networks = state.linkedNetworks,
                selectedIds = state.selectedNetworkIds,
                enabled = !state.isPosting,
                onToggleNetwork = onToggleNetwork,
            )

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss, enabled = !state.isPosting) { Text("Cancel") }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onPost,
                    enabled = state.canPost,
                    modifier = Modifier.testTag(MessagesFeedTags.COMPOSE_SUBMIT),
                ) {
                    if (state.isPosting || state.isUploading) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(if (state.isScheduled) "Schedule" else "Post")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachmentRow(
    attachments: List<PendingAttachment>,
    onRemove: (PendingAttachment) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        attachments.forEach { attachment ->
            AssistChip(
                onClick = { onRemove(attachment) },
                label = {
                    Text(
                        text = when {
                            attachment.isUploading -> "Uploading…"
                            attachment.isVideo -> "Video"
                            else -> "Image"
                        },
                    )
                },
                leadingIcon = {
                    if (attachment.isUploading) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            if (attachment.isVideo) Icons.Filled.Videocam else Icons.Filled.Image,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                },
                trailingIcon = {
                    Icon(Icons.Filled.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                },
            )
        }
    }
}

/**
 * The tag input: the tags already added, a field to type the next one, and the
 * server's prefix suggestions underneath.
 *
 * A tag is a **free-form label**, not a hashtag — the live API happily returns
 * tags containing spaces and punctuation — so the field never splits what is
 * typed. A tag is committed deliberately: by tapping Add, by pressing the
 * keyboard's Done action, or by tapping one of the suggestions.
 *
 * The suggestions are rendered in the order the server sent them and are not
 * re-filtered here: matching is the server's case-insensitive literal prefix,
 * and second-guessing it would show (or hide) results it would never have given.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TagInput(
    tags: List<String>,
    query: String,
    suggestions: List<TagSuggestion>,
    canCommit: Boolean,
    isLoadingSuggestions: Boolean,
    enabled: Boolean,
    onQueryChange: (String) -> Unit,
    onCommit: () -> Unit,
    onSelectSuggestion: (TagSuggestion) -> Unit,
    onRemoveTag: (String) -> Unit,
) {
    Column {
        Text(
            text = "Tags",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (tags.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().testTag(MessagesFeedTags.COMPOSE_TAGS),
            ) {
                tags.forEach { tag ->
                    InputChip(
                        selected = true,
                        onClick = { onRemoveTag(tag) },
                        enabled = enabled,
                        label = { Text(tag) },
                        trailingIcon = {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Remove tag $tag",
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        modifier = Modifier.testTag(MessagesFeedTags.composeTagTag(tag)),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            enabled = enabled,
            singleLine = true,
            placeholder = { Text("Add a tag") },
            leadingIcon = {
                // The lookup is debounced, so say when one is actually running.
                if (isLoadingSuggestions) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Sell, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            },
            trailingIcon = {
                TextButton(
                    onClick = onCommit,
                    enabled = enabled && canCommit,
                    modifier = Modifier.testTag(MessagesFeedTags.COMPOSE_TAG_ADD),
                ) {
                    Text("Add")
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onCommit() }),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(MessagesFeedTags.COMPOSE_TAG_INPUT),
        )
        if (suggestions.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().testTag(MessagesFeedTags.TAG_SUGGESTIONS),
            ) {
                suggestions.forEach { suggestion ->
                    AssistChip(
                        onClick = { onSelectSuggestion(suggestion) },
                        enabled = enabled,
                        label = { Text(suggestion.chipLabel) },
                        modifier = Modifier.testTag(
                            MessagesFeedTags.tagSuggestionTag(suggestion.tag),
                        ),
                    )
                }
            }
        }
    }
}

/** A suggestion reads as the tag itself, with its usage count when it has one. */
private val TagSuggestion.chipLabel: String
    get() = if (count > 0) "$tag ($count)" else tag

/**
 * The Public / Private control. Seeded from the account's default-visibility
 * preference and overridable for this message only; the selection is always sent
 * explicitly so the server default never silently decides. A short hint spells out
 * what "Private" means, since the consequence (nobody else sees the post) is not
 * recoverable from the chip alone.
 *
 * A push/quote is always public ([MessageVisibility.PUSH_OR_QUOTE]), so when
 * [canChangeVisibility] is false the Private chip is not offered at all and
 * Public is shown locked — the banner above the control explains why.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VisibilityRow(
    visibility: MessageVisibility,
    enabled: Boolean,
    canChangeVisibility: Boolean,
    onVisibilityChange: (MessageVisibility) -> Unit,
) {
    Column {
        Text(
            text = "Visibility",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = visibility == MessageVisibility.PUBLIC,
                onClick = { onVisibilityChange(MessageVisibility.PUBLIC) },
                enabled = enabled && canChangeVisibility,
                label = { Text("Public") },
                leadingIcon = {
                    Icon(Icons.Filled.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                modifier = Modifier.testTag(MessagesFeedTags.VISIBILITY_PUBLIC),
            )
            if (canChangeVisibility) {
                FilterChip(
                    selected = visibility == MessageVisibility.PRIVATE,
                    onClick = { onVisibilityChange(MessageVisibility.PRIVATE) },
                    enabled = enabled,
                    label = { Text("Private") },
                    leadingIcon = {
                        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier.testTag(MessagesFeedTags.VISIBILITY_PRIVATE),
                )
            }
        }
        if (visibility == MessageVisibility.PRIVATE) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Only you will see this message.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(MessagesFeedTags.VISIBILITY_HINT),
            )
        }
    }
}

/**
 * The message this compose will quote, shown inset so the user can see exactly
 * what they are re-sharing before they send it.
 */
@Composable
private fun QuotedMessage(quoted: Message) {
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(12.dp)
            .testTag(MessagesFeedTags.QUOTE_ATTACHED),
    ) {
        Text(
            text = quoted.authorLabel,
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = quoted.content,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The banner that confirms, before sending, that a quote is public — matching the
 * web. It states the rule the app enforces via [MessageVisibility.PUSH_OR_QUOTE],
 * so the user is never surprised by where their post ends up.
 */
@Composable
private fun AlwaysPublicBanner() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MessagesFeedTags.QUOTE_PUBLIC_BANNER),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(
                Icons.Filled.Public,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Pushes and quotes are always public.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The cross-post destinations row: InterlinedList is always-on (rendered as a
 * disabled, always-selected chip), followed by a toggle chip per already-linked
 * network. When nothing is linked, a subtle hint points the user to the web to
 * link accounts — the app does not build an OAuth connect flow.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DestinationsRow(
    networks: List<LinkedNetwork>,
    selectedIds: Set<String>,
    enabled: Boolean,
    onToggleNetwork: (String) -> Unit,
) {
    Column {
        Text(
            text = "Post to",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // InterlinedList is always a destination; shown selected and locked.
            FilterChip(
                selected = true,
                onClick = {},
                enabled = false,
                label = { Text("InterlinedList") },
                leadingIcon = {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                modifier = Modifier.testTag(MessagesFeedTags.DESTINATION_IL),
            )
            networks.forEach { network ->
                val isSelected = network.id in selectedIds
                FilterChip(
                    selected = isSelected,
                    onClick = { onToggleNetwork(network.id) },
                    enabled = enabled,
                    label = { Text(network.chipLabel) },
                    leadingIcon = if (isSelected) {
                        {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    } else {
                        null
                    },
                    modifier = Modifier.testTag(MessagesFeedTags.destinationTag(network.id)),
                )
            }
        }
        if (networks.isEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Link accounts on the web to cross-post.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(MessagesFeedTags.DESTINATIONS_HINT),
            )
        }
    }
}

/**
 * A brief, dismissible banner surfacing the per-network cross-post statuses
 * returned by the create endpoint. Anchored to the bottom of the screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CrossPostStatusBanner(
    statuses: List<CrossPostStatus>,
    onDismiss: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Surface(
            tonalElevation = 3.dp,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp)
                .testTag(MessagesFeedTags.CROSS_POST_STATUS),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = "Cross-post results",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(8.dp))
                statuses.forEach { status ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val icon = if (status.isFailed) Icons.Filled.Close else Icons.Filled.Check
                        val tint = when {
                            status.isFailed -> MaterialTheme.colorScheme.error
                            status.isSuccess -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
                        Text(
                            text = buildString {
                                append(status.label)
                                append(": ")
                                append(
                                    when {
                                        status.isFailed -> status.error ?: "Failed"
                                        status.isSuccess -> "Posted"
                                        else -> status.status
                                    },
                                )
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Dismiss") }
                }
            }
        }
    }
}

/**
 * Toggle chip for scheduling. To stay device- and dialog-independent (and easily
 * testable), tapping sets a fixed "1 hour from now" ISO time; tapping again clears
 * it. A full date/time picker can replace this without touching the ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleChip(
    scheduledAt: String?,
    enabled: Boolean,
    onSchedule: (String?) -> Unit,
) {
    AssistChip(
        onClick = {
            if (scheduledAt != null) {
                onSchedule(null)
            } else {
                onSchedule(Instant.now().plus(1, ChronoUnit.HOURS).toString())
            }
        },
        enabled = enabled,
        label = { Text(if (scheduledAt != null) "Scheduled" else "Schedule") },
        leadingIcon = {
            Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
        },
        modifier = Modifier.testTag(MessagesFeedTags.COMPOSE_SCHEDULE),
    )
}

@Preview(showBackground = true)
@Composable
private fun MessagesFeedPreview() {
    InterlinedListTheme {
        MessagesFeedScreen(
            state = MessagesFeedUiState(
                messages = listOf(
                    Message(
                        id = "1", content = "Shipping the Android messages feed today.",
                        authorId = "u1", authorUsername = "adron", authorDisplayName = "Adron",
                        authorAvatarUrl = null, createdAt = null, digCount = 4, replyCount = 2,
                        dugByMe = true, parentId = null, mine = true,
                    ),
                ),
            ),
            onRefresh = {}, onLoadMore = {}, onOpenMessage = {}, onDig = {}, onDelete = {},
            onOpenCompose = {}, onDismissCompose = {}, onComposeTextChange = {}, onPost = {},
        )
    }
}
