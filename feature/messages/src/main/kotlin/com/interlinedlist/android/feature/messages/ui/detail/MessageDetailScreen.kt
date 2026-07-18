package com.interlinedlist.android.feature.messages.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

/** Stable test tags for the detail screen. */
object MessageDetailTags {
    const val ROOT = "messageDetailRoot"
    const val REPLIES = "messageDetailReplies"
    const val REPLY_INPUT = "messageDetailReplyInput"
    const val REPLY_SEND = "messageDetailReplySend"
    const val PROGRESS = "messageDetailProgress"
    const val ERROR = "messageDetailError"
    const val LOCKED = "messageDetailLocked"
}

/**
 * Hilt-wired detail entry point. Reads its message id from the nav argument
 * ([MESSAGE_ID_ARG]) via SavedStateHandle.
 *
 * @param onBack pops the detail screen off the back stack.
 * @param onOpenMessage navigates into a reply (which is itself a message).
 */
@Composable
fun MessageDetailRoute(
    onBack: () -> Unit,
    onOpenMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MessageDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MessageDetailScreen(
        state = state,
        onBack = onBack,
        onOpenMessage = onOpenMessage,
        onDig = viewModel::onDig,
        onReplyTextChange = viewModel::onReplyTextChange,
        onPostReply = viewModel::postReply,
        onRetry = viewModel::load,
        modifier = modifier,
    )
}

/** Stateless detail UI: the message, a reply composer, and the reply list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageDetailScreen(
    state: MessageDetailUiState,
    onBack: () -> Unit,
    onOpenMessage: (String) -> Unit,
    onDig: () -> Unit,
    onReplyTextChange: (String) -> Unit,
    onPostReply: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag(MessageDetailTags.ROOT),
        topBar = {
            TopAppBar(
                title = { Text("Thread") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.subscriptionRequired -> Locked(state.errorMessage, Modifier.padding(padding))
            state.message == null && state.isLoading -> Loading(Modifier.padding(padding))
            state.message == null && state.errorMessage != null ->
                ErrorState(state.errorMessage, onRetry, Modifier.padding(padding))
            else -> Content(
                state = state,
                contentPadding = padding,
                onOpenMessage = onOpenMessage,
                onDig = onDig,
                onReplyTextChange = onReplyTextChange,
                onPostReply = onPostReply,
            )
        }
    }
}

@Composable
private fun Content(
    state: MessageDetailUiState,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    onOpenMessage: (String) -> Unit,
    onDig: () -> Unit,
    onReplyTextChange: (String) -> Unit,
    onPostReply: () -> Unit,
) {
    val message = state.message
    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .testTag(MessageDetailTags.REPLIES),
        ) {
            if (message != null) {
                item {
                    MessageCard(
                        message = message,
                        onClick = {},
                        onDig = onDig,
                        onDelete = {},
                    )
                    HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = "Replies",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(state.replies, key = { it.id }) { reply ->
                MessageCard(
                    message = reply,
                    onClick = { onOpenMessage(reply.id) },
                    onDig = {},
                    onDelete = {},
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        ReplyComposer(
            text = state.replyText,
            canReply = state.canReply,
            isPosting = state.isPostingReply,
            onTextChange = onReplyTextChange,
            onSend = onPostReply,
        )
    }
}

@Composable
private fun ReplyComposer(
    text: String,
    canReply: Boolean,
    isPosting: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = { Text("Write a reply…") },
            enabled = !isPosting,
            modifier = Modifier
                .weight(1f)
                .testTag(MessageDetailTags.REPLY_INPUT),
        )
        Spacer(Modifier.height(8.dp))
        IconButton(
            onClick = onSend,
            enabled = canReply,
            modifier = Modifier.testTag(MessageDetailTags.REPLY_SEND),
        ) {
            if (isPosting) {
                CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send reply")
            }
        }
    }
}

@Composable
private fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.testTag(MessageDetailTags.PROGRESS))
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag(MessageDetailTags.ERROR),
        )
        Spacer(Modifier.height(16.dp))
        androidx.compose.material3.Button(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun Locked(message: String?, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = message ?: "This thread requires an active subscription.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(MessageDetailTags.LOCKED),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MessageDetailPreview() {
    InterlinedListTheme {
        MessageDetailScreen(
            state = MessageDetailUiState(
                message = Message(
                    id = "1", content = "Parent message",
                    authorId = "u1", authorUsername = "adron", authorDisplayName = "Adron",
                    authorAvatarUrl = null, createdAt = null, digCount = 1, replyCount = 1,
                    dugByMe = false, parentId = null, mine = false,
                ),
                replies = listOf(
                    Message(
                        id = "2", content = "A reply",
                        authorId = "u2", authorUsername = "guest", authorDisplayName = null,
                        authorAvatarUrl = null, createdAt = null, digCount = 0, replyCount = 0,
                        dugByMe = false, parentId = "1", mine = false,
                    ),
                ),
            ),
            onBack = {}, onOpenMessage = {}, onDig = {}, onReplyTextChange = {},
            onPostReply = {}, onRetry = {},
        )
    }
}
