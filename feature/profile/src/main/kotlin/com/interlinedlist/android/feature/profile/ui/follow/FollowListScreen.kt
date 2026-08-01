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

/** Stable test tags for the followers / following lists. */
object FollowListTestTags {
    const val LIST = "followList"
    const val EMPTY = "followListEmpty"
    const val PROGRESS = "followListProgress"
    const val ERROR = "followListError"
    const val BACK = "followListBack"
    fun row(username: String) = "followListRow_$username"
}

/**
 * The followers list for a user (route `followers/{username}`). Tapping a row drills
 * down into that user's profile, mirroring the drill-down navigation pattern.
 *
 * @param onOpenUser navigate to `profile/{username}` for the tapped user.
 * @param onBack pop back to the previous screen.
 */
@Composable
fun FollowersRoute(
    onOpenUser: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FollowersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    FollowListScreen(
        title = "Followers",
        state = state,
        onOpenUser = onOpenUser,
        onBack = onBack,
        onRetry = viewModel::refresh,
        modifier = modifier,
    )
}

/**
 * The following list for a user (route `following/{username}`). Tapping a row drills
 * down into that user's profile.
 *
 * @param onOpenUser navigate to `profile/{username}` for the tapped user.
 * @param onBack pop back to the previous screen.
 */
@Composable
fun FollowingRoute(
    onOpenUser: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FollowingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    FollowListScreen(
        title = "Following",
        state = state,
        onOpenUser = onOpenUser,
        onBack = onBack,
        onRetry = viewModel::refresh,
        modifier = modifier,
    )
}

/** Stateless followers / following list UI. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FollowListScreen(
    title: String,
    state: FollowListUiState,
    onOpenUser: (String) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(FollowListTestTags.BACK)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(
                    Modifier.align(Alignment.Center).testTag(FollowListTestTags.PROGRESS),
                )

                state.errorMessage != null -> Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = state.errorMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag(FollowListTestTags.ERROR),
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRetry) { Text("Retry") }
                }

                state.isEmpty -> Text(
                    text = "No one here yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).testTag(FollowListTestTags.EMPTY),
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag(FollowListTestTags.LIST),
                ) {
                    items(state.users, key = { it.id }) { user ->
                        FollowUserRow(user = user, onClick = { onOpenUser(user.username) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun FollowUserRow(user: FollowUser, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(FollowListTestTags.row(user.username))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(avatarUrl = user.avatarUrl, seedLabel = user.displayLabel, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column {
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
    }
}

@Preview(showBackground = true)
@Composable
private fun FollowListScreenPreview() {
    InterlinedListTheme {
        FollowListScreen(
            title = "Followers",
            state = FollowListUiState(
                users = listOf(
                    FollowUser("1", "ada", "Ada Lovelace", null),
                    FollowUser("2", "adron", "Adron Hall", null),
                ),
                isLoading = false,
            ),
            onOpenUser = {},
            onBack = {},
            onRetry = {},
        )
    }
}
