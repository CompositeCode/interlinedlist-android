package com.interlinedlist.android.feature.notifications.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.interlinedlist.android.feature.notifications.domain.Notification
import com.interlinedlist.android.feature.notifications.domain.NotificationTarget
import com.interlinedlist.android.feature.notifications.domain.NotificationType
import com.interlinedlist.android.feature.notifications.ui.components.NotificationRow

/** Stable test tags for the notifications screen. */
object NotificationsTags {
    const val LIST = "notificationsList"
    const val EMPTY = "notificationsEmpty"
    const val ERROR = "notificationsError"
    const val LOCKED = "notificationsLocked"
    const val PROGRESS = "notificationsProgress"
    const val BACK = "notificationsBack"
    const val MARK_ALL_READ = "notificationsMarkAllRead"
    const val UNREAD_BADGE = "notificationsUnreadBadge"
    const val PREFERENCES = "notificationsPreferences"
}

/**
 * Hilt-wired notifications entry point. Reached from the Account hub as a drill-down;
 * mirrors the back pattern used by the other detail screens.
 *
 * @param onBack pops the notifications screen off the back stack.
 * @param onOpenTarget optional deep-link handler. When a tapped notification carries a
 *   [NotificationTarget] (a message / user / list it refers to), this is invoked with
 *   that target so the app can navigate to it; the notification is marked read either
 *   way. Defaults to a no-op, which keeps the screen a self-contained list — pass a
 *   real handler only when the app is ready to route on notification targets.
 */
@Composable
fun NotificationsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenTarget: (NotificationTarget) -> Unit = {},
    onOpenPreferences: () -> Unit = {},
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    NotificationsScreen(
        state = state,
        onBack = onBack,
        onOpenPreferences = onOpenPreferences,
        onRefresh = viewModel::refresh,
        onLoadMore = viewModel::loadMore,
        onMarkAllRead = viewModel::onMarkAllRead,
        onOpen = { notification ->
            // Marking read is offline-first; navigation (if any) is the app's concern.
            viewModel.onOpen(notification)
            notification.target?.let(onOpenTarget)
        },
        onDismiss = viewModel::onDismiss,
        modifier = modifier,
    )
}

/** Stateless notifications UI — drives all list/empty/error/locked states from [state]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    state: NotificationsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onMarkAllRead: () -> Unit,
    onOpen: (Notification) -> Unit,
    onDismiss: (Notification) -> Unit,
    modifier: Modifier = Modifier,
    onOpenPreferences: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    if (state.hasUnread) {
                        BadgedBox(
                            badge = {
                                Badge(modifier = Modifier.testTag(NotificationsTags.UNREAD_BADGE)) {
                                    Text(state.unreadCount.coerceAtMost(99).toString())
                                }
                            },
                        ) {
                            Text("Notifications")
                        }
                    } else {
                        Text("Notifications")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(NotificationsTags.BACK)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.hasUnread && !state.subscriptionRequired) {
                        IconButton(
                            onClick = onMarkAllRead,
                            modifier = Modifier.testTag(NotificationsTags.MARK_ALL_READ),
                        ) {
                            Icon(Icons.Filled.DoneAll, contentDescription = "Mark all read")
                        }
                    }
                    IconButton(
                        onClick = onOpenPreferences,
                        modifier = Modifier.testTag(NotificationsTags.PREFERENCES),
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = "Notification preferences")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.subscriptionRequired -> LockedState(
                message = state.errorMessage,
                modifier = Modifier.padding(padding),
            )
            else -> Content(
                state = state,
                contentPadding = padding,
                onRefresh = onRefresh,
                onLoadMore = onLoadMore,
                onOpen = onOpen,
                onDismiss = onDismiss,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Content(
    state: NotificationsUiState,
    contentPadding: PaddingValues,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (Notification) -> Unit,
    onDismiss: (Notification) -> Unit,
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
            else -> NotificationList(
                state = state,
                onLoadMore = onLoadMore,
                onOpen = onOpen,
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun NotificationList(
    state: NotificationsUiState,
    onLoadMore: () -> Unit,
    onOpen: (Notification) -> Unit,
    onDismiss: (Notification) -> Unit,
) {
    val listState = rememberLazyListState()
    // Trigger load-more when the last item scrolls into view.
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            state.canLoadMore && !state.isLoadingMore && last >= state.notifications.size - 3
        }
    }
    if (shouldLoadMore) onLoadMore()

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .testTag(NotificationsTags.LIST),
    ) {
        items(state.notifications, key = { it.id }) { notification ->
            NotificationRow(
                notification = notification,
                onClick = { onOpen(notification) },
                onDismiss = { onDismiss(notification) },
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
        CircularProgressIndicator(Modifier.testTag(NotificationsTags.PROGRESS))
    }
}

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = "You're all caught up. No notifications yet.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(NotificationsTags.EMPTY),
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
            modifier = Modifier.testTag(NotificationsTags.ERROR),
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
            text = message ?: "Upgrade to an active subscription to view your notifications.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(NotificationsTags.LOCKED),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NotificationsPreview() {
    InterlinedListTheme {
        NotificationsScreen(
            state = NotificationsUiState(
                notifications = listOf(
                    Notification(
                        id = "1", type = NotificationType.FOLLOW, actor = null,
                        subject = "Amy started following you", body = null,
                        createdAt = null, read = false, target = null,
                    ),
                    Notification(
                        id = "2", type = NotificationType.REPLY, actor = null,
                        subject = "Ben replied to your post", body = "\"Great idea!\"",
                        createdAt = null, read = true, target = null,
                    ),
                ),
                unreadCount = 1,
            ),
            onBack = {}, onRefresh = {}, onLoadMore = {}, onMarkAllRead = {},
            onOpen = {}, onDismiss = {},
        )
    }
}
