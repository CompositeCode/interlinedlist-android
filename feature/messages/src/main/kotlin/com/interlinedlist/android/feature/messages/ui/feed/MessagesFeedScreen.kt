package com.interlinedlist.android.feature.messages.ui.feed

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.messages.domain.Message
import com.interlinedlist.android.feature.messages.ui.components.MessageCard

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
}

/**
 * Hilt-wired feed entry point. The app's NavHost hosts this as the Messages tab.
 *
 * @param onOpenMessage navigates to the detail screen for the given message id.
 */
@Composable
fun MessagesRoute(
    onOpenMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MessagesFeedViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MessagesFeedScreen(
        state = state,
        onRefresh = viewModel::refresh,
        onLoadMore = viewModel::loadMore,
        onOpenMessage = onOpenMessage,
        onDig = viewModel::onDig,
        onDelete = viewModel::onDelete,
        onOpenCompose = viewModel::openCompose,
        onDismissCompose = viewModel::dismissCompose,
        onComposeTextChange = viewModel::onComposeTextChange,
        onPost = viewModel::post,
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
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("Messages") }) },
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
            )
        }
    }

    if (state.isComposeOpen) {
        ComposeSheet(
            text = state.composeText,
            isPosting = state.isPosting,
            canPost = state.canPost,
            onTextChange = onComposeTextChange,
            onDismiss = onDismissCompose,
            onPost = onPost,
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
    text: String,
    isPosting: Boolean,
    canPost: Boolean,
    onTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onPost: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text("New message", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = { Text("What's on your mind?") },
                enabled = !isPosting,
                minLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(MessagesFeedTags.COMPOSE_INPUT),
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss, enabled = !isPosting) { Text("Cancel") }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onPost,
                    enabled = canPost,
                    modifier = Modifier.testTag(MessagesFeedTags.COMPOSE_SUBMIT),
                ) {
                    if (isPosting) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("Post")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
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
