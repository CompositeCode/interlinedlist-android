package com.interlinedlist.android.feature.profile.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import com.interlinedlist.android.feature.profile.domain.ModeratedUser

/** Stable test tags for the "Blocked & muted" management screen. */
object BlockedMutedTestTags {
    const val LIST = "blockedMutedList"
    const val EMPTY = "blockedMutedEmpty"
    const val PROGRESS = "blockedMutedProgress"
    const val ERROR = "blockedMutedError"
    const val BACK = "blockedMutedBack"
    const val BLOCKED_HEADER = "blockedMutedBlockedHeader"
    const val MUTED_HEADER = "blockedMutedMutedHeader"
    fun blockedRow(username: String) = "blockedRow_$username"
    fun mutedRow(username: String) = "mutedRow_$username"
    fun unblock(username: String) = "unblock_$username"
    fun unmute(username: String) = "unmute_$username"
}

/**
 * The "Blocked & muted" management screen (route `account/blocked-muted`), reached from the
 * Account hub. Lists the current user's blocked and muted users; each row can be un-blocked
 * or un-muted, removed optimistically and rolled back on failure.
 *
 * @param onBack pop back to the account hub.
 */
@Composable
fun BlockedMutedRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BlockedMutedViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BlockedMutedScreen(
        state = state,
        onUnblock = viewModel::unblock,
        onUnmute = viewModel::unmute,
        onBack = onBack,
        onRetry = viewModel::refresh,
        modifier = modifier,
    )
}

/** Stateless "Blocked & muted" UI. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BlockedMutedScreen(
    state: BlockedMutedUiState,
    onUnblock: (String) -> Unit,
    onUnmute: (String) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Blocked & muted") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(BlockedMutedTestTags.BACK)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(
                    Modifier.align(Alignment.Center).testTag(BlockedMutedTestTags.PROGRESS),
                )

                state.errorMessage != null && state.blocked.isEmpty() && state.muted.isEmpty() -> ErrorState(
                    message = state.errorMessage,
                    onRetry = onRetry,
                    tag = BlockedMutedTestTags.ERROR,
                )

                state.isEmpty -> Text(
                    text = "You haven't blocked or muted anyone.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                        .testTag(BlockedMutedTestTags.EMPTY),
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag(BlockedMutedTestTags.LIST),
                ) {
                    if (state.blocked.isNotEmpty()) {
                        item(key = "blocked-header") {
                            SectionHeader("Blocked", BlockedMutedTestTags.BLOCKED_HEADER)
                        }
                        items(state.blocked, key = { "blocked-${it.username}" }) { user ->
                            ModeratedRow(
                                user = user,
                                actionLabel = "Unblock",
                                rowTag = BlockedMutedTestTags.blockedRow(user.username),
                                actionTag = BlockedMutedTestTags.unblock(user.username),
                                inProgress = user.username in state.pendingIds,
                                onAction = { onUnblock(user.username) },
                            )
                            HorizontalDivider()
                        }
                    }
                    if (state.muted.isNotEmpty()) {
                        item(key = "muted-header") {
                            SectionHeader("Muted", BlockedMutedTestTags.MUTED_HEADER)
                        }
                        items(state.muted, key = { "muted-${it.username}" }) { user ->
                            ModeratedRow(
                                user = user,
                                actionLabel = "Unmute",
                                rowTag = BlockedMutedTestTags.mutedRow(user.username),
                                actionTag = BlockedMutedTestTags.unmute(user.username),
                                inProgress = user.username in state.pendingIds,
                                onAction = { onUnmute(user.username) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(label: String, tag: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(tag),
    )
}

@Composable
private fun ModeratedRow(
    user: ModeratedUser,
    actionLabel: String,
    rowTag: String,
    actionTag: String,
    inProgress: Boolean,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(rowTag)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = user.displayLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "@${user.username}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (inProgress) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            OutlinedButton(onClick = onAction, modifier = Modifier.testTag(actionTag)) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit, tag: String) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(tag),
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}

@Preview(showBackground = true)
@Composable
private fun BlockedMutedScreenPreview() {
    InterlinedListTheme {
        BlockedMutedScreen(
            state = BlockedMutedUiState(
                blocked = listOf(
                    ModeratedUser("b1", "spammer", "Spam Bot", null),
                ),
                muted = listOf(
                    ModeratedUser("m1", "noisy", "Very Loud", null),
                ),
                isLoading = false,
            ),
            onUnblock = {},
            onUnmute = {},
            onBack = {},
            onRetry = {},
        )
    }
}
