package com.interlinedlist.android.feature.profile.ui.follow

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.interlinedlist.android.feature.profile.domain.FollowUser
import com.interlinedlist.android.feature.profile.ui.common.UserAvatar

/** Stable test tags for the follow-requests screen. */
object FollowRequestsTestTags {
    const val LIST = "followRequestsList"
    const val EMPTY = "followRequestsEmpty"
    const val PROGRESS = "followRequestsProgress"
    const val ERROR = "followRequestsError"
    const val BACK = "followRequestsBack"
    fun approve(username: String) = "followRequestApprove_$username"
    fun reject(username: String) = "followRequestReject_$username"
    fun row(username: String) = "followRequestRow_$username"
}

/**
 * The current user's pending follow requests (private accounts). Approve/reject each
 * request; tapping a row drills into the requester's profile.
 *
 * @param onOpenUser navigate to `profile/{username}` for the tapped requester.
 * @param onBack pop back to the account hub.
 */
@Composable
fun FollowRequestsRoute(
    onOpenUser: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FollowRequestsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    FollowRequestsScreen(
        state = state,
        onApprove = viewModel::approve,
        onReject = viewModel::reject,
        onOpenUser = onOpenUser,
        onBack = onBack,
        onRetry = viewModel::refresh,
        modifier = modifier,
    )
}

/** Stateless follow-requests UI. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FollowRequestsScreen(
    state: FollowRequestsUiState,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
    onOpenUser: (String) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Follow requests") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(FollowRequestsTestTags.BACK)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(
                    Modifier.align(Alignment.Center).testTag(FollowRequestsTestTags.PROGRESS),
                )

                state.errorMessage != null && state.requests.isEmpty() -> Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = state.errorMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag(FollowRequestsTestTags.ERROR),
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRetry) { Text("Retry") }
                }

                state.isEmpty -> Text(
                    text = "No pending requests.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).testTag(FollowRequestsTestTags.EMPTY),
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag(FollowRequestsTestTags.LIST),
                ) {
                    items(state.requests, key = { it.id }) { user ->
                        FollowRequestRow(
                            user = user,
                            inProgress = user.id in state.pendingActionIds,
                            onOpen = { onOpenUser(user.username) },
                            onApprove = { onApprove(user.id) },
                            onReject = { onReject(user.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun FollowRequestRow(
    user: FollowUser,
    inProgress: Boolean,
    onOpen: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .testTag(FollowRequestsTestTags.row(user.username))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(avatarUrl = user.avatarUrl, seedLabel = user.displayLabel, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = user.displayLabel,
                style = MaterialTheme.typography.titleMedium,
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
            OutlinedButton(
                onClick = onReject,
                modifier = Modifier.testTag(FollowRequestsTestTags.reject(user.username)),
            ) {
                Text("Reject")
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onApprove,
                modifier = Modifier.testTag(FollowRequestsTestTags.approve(user.username)),
            ) {
                Text("Approve")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FollowRequestsScreenPreview() {
    InterlinedListTheme {
        FollowRequestsScreen(
            state = FollowRequestsUiState(
                requests = listOf(
                    FollowUser("1", "ada", "Ada Lovelace", null),
                    FollowUser("2", "grace", "Grace Hopper", null),
                ),
                isLoading = false,
            ),
            onApprove = {},
            onReject = {},
            onOpenUser = {},
            onBack = {},
            onRetry = {},
        )
    }
}
