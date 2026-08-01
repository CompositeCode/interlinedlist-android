package com.interlinedlist.android.feature.messages.ui.scheduled

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
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.interlinedlist.android.feature.messages.ui.relativeTime

/** Stable test tags for the scheduled-messages screen. */
object ScheduledMessagesTags {
    const val LIST = "scheduledList"
    const val EMPTY = "scheduledEmpty"
    const val ERROR = "scheduledError"
    const val LOCKED = "scheduledLocked"
    const val PROGRESS = "scheduledProgress"
    const val CANCEL = "scheduledCancel"
}

/**
 * Hilt-wired scheduled-messages entry point, pushed onto the back stack from the
 * feed (mirrors the drill-down nav pattern).
 *
 * @param onBack pops this screen off the back stack.
 */
@Composable
fun ScheduledMessagesRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScheduledMessagesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ScheduledMessagesScreen(
        state = state,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onCancel = viewModel::cancel,
        modifier = modifier,
    )
}

/** Stateless scheduled-messages UI: list / empty / error / locked states. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledMessagesScreen(
    state: ScheduledUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onCancel: (Message) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Scheduled") },
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
            else -> Content(state = state, contentPadding = padding, onRefresh = onRefresh, onCancel = onCancel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Content(
    state: ScheduledUiState,
    contentPadding: PaddingValues,
    onRefresh: () -> Unit,
    onCancel: (Message) -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        when {
            state.isEmpty && state.isRefreshing -> Loading()
            state.isEmpty && state.errorMessage != null -> ErrorState(state.errorMessage, onRefresh)
            state.isEmpty -> EmptyState()
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(ScheduledMessagesTags.LIST),
            ) {
                items(state.messages, key = { it.id }) { message ->
                    ScheduledRow(message = message, onCancel = { onCancel(message) })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun ScheduledRow(message: Message, onCancel: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.height(16.dp),
                )
                Spacer(Modifier.height(0.dp))
                Text(
                    text = " " + sendLabel(message.scheduledAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = message.content.ifBlank { "(media only)" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
            )
        }
        TextButton(
            onClick = onCancel,
            modifier = Modifier.testTag(ScheduledMessagesTags.CANCEL),
        ) {
            Text("Cancel")
        }
    }
}

/** A short "sends in …" label derived from the scheduled time. */
private fun sendLabel(scheduledAt: String?): String {
    val rel = relativeTime(scheduledAt)
    return if (rel.isBlank()) "Scheduled" else "Sends in $rel"
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.testTag(ScheduledMessagesTags.PROGRESS))
    }
}

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = "No scheduled messages. Schedule one from the compose sheet.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(ScheduledMessagesTags.EMPTY),
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
            modifier = Modifier.testTag(ScheduledMessagesTags.ERROR),
        )
        Spacer(Modifier.height(16.dp))
        androidx.compose.material3.Button(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun Locked(message: String?, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = message ?: "Scheduled messages require an active subscription.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(ScheduledMessagesTags.LOCKED),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ScheduledPreview() {
    InterlinedListTheme {
        ScheduledMessagesScreen(
            state = ScheduledUiState(
                messages = listOf(
                    Message(
                        id = "1", content = "Goes out tomorrow morning.",
                        authorId = "u1", authorUsername = "adron", authorDisplayName = "Adron",
                        authorAvatarUrl = null, createdAt = null, digCount = 0, replyCount = 0,
                        dugByMe = false, parentId = null, mine = true,
                        scheduledAt = "2026-07-19T09:00:00Z",
                    ),
                ),
            ),
            onBack = {}, onRefresh = {}, onCancel = {},
        )
    }
}
