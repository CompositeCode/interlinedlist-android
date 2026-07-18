package com.interlinedlist.android.feature.lists.ui.watchers

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import com.interlinedlist.android.feature.lists.domain.Watcher
import com.interlinedlist.android.feature.lists.domain.WatcherCandidate
import com.interlinedlist.android.feature.lists.domain.WatcherRole

/** Stable test tags for the watchers screen. */
object WatchersTestTags {
    const val LIST = "watchersList"
    const val SEARCH = "watchersSearch"
    const val EMPTY = "watchersEmpty"
    const val PROGRESS = "watchersProgress"
    const val ERROR = "watchersError"
    fun watcher(userId: String) = "watcher_$userId"
    fun remove(userId: String) = "watcherRemove_$userId"
    fun candidate(userId: String) = "watcherCandidate_$userId"
}

/**
 * Hilt-wired entry for a list's watchers. Reads its `listId` from the nav
 * SavedStateHandle (see [WATCHERS_LIST_ID_ARG]); [onBack] pops navigation.
 */
@Composable
fun WatchersRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WatchersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    WatchersScreen(
        state = state,
        onBack = onBack,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onAddCandidate = { viewModel.addWatcher(it) },
        onChangeRole = viewModel::changeRole,
        onRemoveWatcher = viewModel::removeWatcher,
        modifier = modifier,
    )
}

/** Stateless watchers screen — list watchers, change roles, add/remove watchers. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchersScreen(
    state: WatchersUiState,
    onBack: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onAddCandidate: (WatcherCandidate) -> Unit,
    onChangeRole: (Watcher, WatcherRole) -> Unit,
    onRemoveWatcher: (Watcher) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Watchers") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(Modifier.testTag(WatchersTestTags.PROGRESS)) }

            else -> Column(Modifier.padding(padding)) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchQueryChange,
                    label = { Text("Add a watcher") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag(WatchersTestTags.SEARCH),
                )

                if (state.errorMessage != null) {
                    Text(
                        text = state.errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .testTag(WatchersTestTags.ERROR),
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(WatchersTestTags.LIST),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.candidates.isNotEmpty()) {
                        item {
                            Text("Suggestions", style = MaterialTheme.typography.labelLarge)
                        }
                        items(state.candidates, key = { "candidate-${it.userId}" }) { candidate ->
                            CandidateRow(candidate = candidate, onAdd = { onAddCandidate(candidate) })
                        }
                    }

                    if (state.isEmpty && state.candidates.isEmpty()) {
                        item { EmptyState() }
                    } else {
                        items(state.watchers, key = { it.userId }) { watcher ->
                            WatcherRow(
                                watcher = watcher,
                                onChangeRole = { onChangeRole(watcher, it) },
                                onRemove = { onRemoveWatcher(watcher) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WatcherRow(
    watcher: Watcher,
    onChangeRole: (WatcherRole) -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(WatchersTestTags.watcher(watcher.userId)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = watcher.label,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "@${watcher.username}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.testTag(WatchersTestTags.remove(watcher.userId)),
                ) { Icon(Icons.Default.Close, contentDescription = "Remove watcher") }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WatcherRole.entries.forEach { role ->
                    FilterChip(
                        selected = watcher.role == role,
                        onClick = { onChangeRole(role) },
                        label = { Text(role.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CandidateRow(candidate: WatcherCandidate, onAdd: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(WatchersTestTags.candidate(candidate.userId)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(candidate.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "@${candidate.username}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AssistChip(
                onClick = onAdd,
                label = { Text("Add") },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp)
            .testTag(WatchersTestTags.EMPTY),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No watchers yet", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Search above to grant someone access.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WatchersScreenPreview() {
    InterlinedListTheme {
        WatchersScreen(
            state = WatchersUiState(
                watchers = listOf(
                    Watcher("u1", "ada", "Ada Lovelace", null, WatcherRole.EDITOR),
                    Watcher("u2", "grace", null, null, WatcherRole.VIEWER),
                ),
                isLoading = false,
            ),
            onBack = {},
            onSearchQueryChange = {},
            onAddCandidate = {},
            onChangeRole = { _, _ -> },
            onRemoveWatcher = {},
        )
    }
}
