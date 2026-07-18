package com.interlinedlist.android.feature.profile.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.profile.domain.UserSearchResult
import com.interlinedlist.android.feature.profile.ui.common.UserAvatar

/** Stable test tags for user search. */
object UserSearchTestTags {
    const val QUERY = "userSearchQuery"
    const val LIST = "userSearchList"
    const val EMPTY = "userSearchEmpty"
    const val PROGRESS = "userSearchProgress"
    const val ERROR = "userSearchError"
    const val BACK = "userSearchBack"
    fun row(username: String) = "userSearchRow_$username"
}

/**
 * User-search route. Results drill down into a user's profile by username.
 *
 * @param onOpenUser navigate to `profile/{username}` for the tapped user.
 * @param onBack pop back to the account screen.
 */
@Composable
fun UserSearchRoute(
    onOpenUser: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UserSearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    UserSearchScreen(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onOpenUser = onOpenUser,
        onBack = onBack,
        modifier = modifier,
    )
}

/** Stateless user-search UI — a query field over a results list. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun UserSearchScreen(
    state: UserSearchUiState,
    onQueryChange: (String) -> Unit,
    onOpenUser: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Find people") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(UserSearchTestTags.BACK)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                label = { Text("Search users") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(20.dp)
                                .testTag(UserSearchTestTags.PROGRESS),
                            strokeWidth = 2.dp,
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag(UserSearchTestTags.QUERY),
            )

            if (state.errorMessage != null) {
                Text(
                    text = state.errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag(UserSearchTestTags.ERROR),
                )
            }

            when {
                state.isEmptyResult -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No users match \"${state.query.trim()}\".",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag(UserSearchTestTags.EMPTY),
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag(UserSearchTestTags.LIST),
                ) {
                    items(state.results, key = { it.id }) { user ->
                        UserSearchRow(user = user, onClick = { onOpenUser(user.username) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun UserSearchRow(user: UserSearchResult, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(UserSearchTestTags.row(user.username))
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
private fun UserSearchScreenPreview() {
    InterlinedListTheme {
        UserSearchScreen(
            state = UserSearchUiState(
                query = "ada",
                results = listOf(
                    UserSearchResult("1", "ada", "Ada Lovelace", null),
                    UserSearchResult("2", "adron", "Adron Hall", null),
                ),
            ),
            onQueryChange = {},
            onOpenUser = {},
            onBack = {},
        )
    }
}
