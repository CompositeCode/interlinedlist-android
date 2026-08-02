package com.interlinedlist.android.feature.messages.ui.feed

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.messages.domain.Message
import com.interlinedlist.android.feature.messages.domain.ReportReason
import com.interlinedlist.android.feature.messages.ui.components.EditMessageSheet
import com.interlinedlist.android.feature.messages.ui.components.MessageCard
import com.interlinedlist.android.feature.messages.ui.components.ModerationDialog
import com.interlinedlist.android.feature.messages.ui.components.ReportDialog
import com.interlinedlist.android.feature.messages.ui.readMediaBytes
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
}

/**
 * Hilt-wired feed entry point. The app's NavHost hosts this as the Messages tab.
 *
 * @param onOpenMessage navigates to the detail screen for the given message id.
 * @param onOpenScheduled navigates to the Scheduled messages screen.
 */
@Composable
fun MessagesRoute(
    onOpenMessage: (String) -> Unit,
    onOpenScheduled: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MessagesFeedViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    MessagesFeedScreen(
        state = state,
        onRefresh = viewModel::refresh,
        onLoadMore = viewModel::loadMore,
        onOpenMessage = onOpenMessage,
        onOpenScheduled = onOpenScheduled,
        onDig = viewModel::onDig,
        onDelete = viewModel::onDelete,
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
        onScheduleChange = viewModel::onScheduleChange,
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
    onOpenScheduled: () -> Unit = {},
    onReport: (Message) -> Unit = {},
    onEdit: (Message) -> Unit = {},
    onBlockUser: (Message) -> Unit = {},
    onMuteUser: (Message) -> Unit = {},
    onReportUser: (Message) -> Unit = {},
    onFetchMetadata: (Message) -> Unit = {},
    onAttachMedia: (Uri, Boolean) -> Unit = { _, _ -> },
    onRemoveAttachment: (PendingAttachment) -> Unit = {},
    onScheduleChange: (String?) -> Unit = {},
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
                title = { Text("Messages") },
                actions = {
                    IconButton(
                        onClick = onOpenScheduled,
                        modifier = Modifier.testTag(MessagesFeedTags.SCHEDULED_ACTION),
                    ) {
                        Icon(Icons.Filled.Schedule, contentDescription = "Scheduled messages")
                    }
                },
            )
        },
        floatingActionButton = {
            if (!state.subscriptionRequired) {
                FloatingActionButton(
                    onClick = onOpenCompose,
                    modifier = Modifier.testTag(MessagesFeedTags.FAB),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "New message")
                }
            }
        },
    ) { padding ->
        when {
            state.subscriptionRequired -> LockedState(
                message = state.errorMessage,
                modifier = Modifier.padding(padding),
            )
            else -> FeedContent(
                state = state,
                contentPadding = padding,
                onRefresh = onRefresh,
                onLoadMore = onLoadMore,
                onOpenMessage = onOpenMessage,
                onDig = onDig,
                onDelete = onDelete,
                onReport = onReport,
                onEdit = onEdit,
                onBlockUser = onBlockUser,
                onMuteUser = onMuteUser,
                onReportUser = onReportUser,
                onFetchMetadata = onFetchMetadata,
            )
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
            onScheduleChange = onScheduleChange,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedContent(
    state: MessagesFeedUiState,
    contentPadding: PaddingValues,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenMessage: (String) -> Unit,
    onDig: (Message) -> Unit,
    onDelete: (Message) -> Unit,
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
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        when {
            state.isEmpty && state.isRefreshing -> LoadingState()
            state.isEmpty && state.errorMessage != null -> ErrorState(state.errorMessage, onRefresh)
            state.isEmpty -> EmptyState()
            else -> FeedList(
                state = state,
                onLoadMore = onLoadMore,
                onOpenMessage = onOpenMessage,
                onDig = onDig,
                onDelete = onDelete,
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
    onLoadMore: () -> Unit,
    onOpenMessage: (String) -> Unit,
    onDig: (Message) -> Unit,
    onDelete: (Message) -> Unit,
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
private fun EmptyState() {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = "No messages yet. Be the first to post.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(MessagesFeedTags.EMPTY),
        )
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
    onScheduleChange: (String?) -> Unit,
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
                text = if (state.isScheduled) "Schedule message" else "New message",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.composeText,
                onValueChange = onTextChange,
                placeholder = { Text("What's on your mind?") },
                enabled = !state.isPosting,
                minLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(MessagesFeedTags.COMPOSE_INPUT),
            )

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
                ScheduleChip(
                    scheduledAt = state.scheduledAt,
                    enabled = !state.isPosting,
                    onSchedule = onScheduleChange,
                )
            }

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
