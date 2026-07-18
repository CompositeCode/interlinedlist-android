package com.interlinedlist.android.feature.lists.ui.list

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
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
import com.interlinedlist.android.feature.lists.domain.ListSummary

/** Stable test tags for the lists index. */
object ListsTestTags {
    const val LIST = "listsIndex"
    const val SEARCH = "listsSearch"
    const val CREATE_FAB = "listsCreateFab"
    const val ERROR = "listsError"
    const val EMPTY = "listsEmpty"
    const val PROGRESS = "listsProgress"
    const val SUBSCRIPTION = "listsSubscription"
    fun row(id: String) = "listRow_$id"
}

/**
 * Hilt-wired entry point for the Lists index. [onOpenList] receives the tapped
 * list's id so the app can navigate to the detail route.
 */
@Composable
fun ListsRoute(
    onOpenList: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ListsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ListsScreen(
        state = state,
        onOpenList = onOpenList,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onLoadMore = viewModel::loadMore,
        onCreateList = { title -> viewModel.createList(title, description = null, onCreated = { onOpenList(it.id) }) },
        modifier = modifier,
    )
}

/** Stateless lists index — loading / empty / error / subscription / content states. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsScreen(
    state: ListsUiState,
    onOpenList: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onLoadMore: () -> Unit,
    onCreateList: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("Lists") }) },
        floatingActionButton = {
            if (!state.subscriptionRequired) {
                ExtendedFloatingActionButton(
                    onClick = { onCreateList("New list") },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("New list") },
                    modifier = Modifier.testTag(ListsTestTags.CREATE_FAB),
                )
            }
        },
    ) { padding ->
        when {
            state.subscriptionRequired -> SubscriptionGate(
                message = state.errorMessage,
                modifier = Modifier.padding(padding),
            )

            state.visibleLists.isEmpty() && state.isRefreshing -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(Modifier.testTag(ListsTestTags.PROGRESS))
            }

            else -> Column(Modifier.padding(padding)) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchQueryChange,
                    label = { Text("Search lists") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag(ListsTestTags.SEARCH),
                )

                if (state.errorMessage != null) {
                    Text(
                        text = state.errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .testTag(ListsTestTags.ERROR),
                    )
                }

                if (state.isEmpty) {
                    EmptyState()
                } else {
                    ListsList(
                        lists = state.visibleLists,
                        isLoadingMore = state.isLoadingMore,
                        hasMore = state.hasMore && !state.isSearching,
                        onOpenList = onOpenList,
                        onLoadMore = onLoadMore,
                    )
                }
            }
        }
    }
}

@Composable
private fun ListsList(
    lists: List<ListSummary>,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    onOpenList: (String) -> Unit,
    onLoadMore: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag(ListsTestTags.LIST),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(lists, key = { it.id }) { list ->
            ListCard(list = list, onClick = { onOpenList(list.id) })
        }
        if (hasMore) {
            item {
                // Trigger load-more when the sentinel scrolls into view.
                LaunchedLoadMore(onLoadMore)
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    if (isLoadingMore) CircularProgressIndicator(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun LaunchedLoadMore(onLoadMore: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) { onLoadMore() }
}

@Composable
private fun ListCard(list: ListSummary, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ListsTestTags.row(list.id)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = list.title.ifBlank { "Untitled list" },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!list.description.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = list.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${list.itemCount} ${if (list.itemCount == 1) "item" else "items"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (list.isPublic) {
                    Text(
                        text = "Public",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(ListsTestTags.EMPTY),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No lists yet", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Tap New list to create your first one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SubscriptionGate(message: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(ListsTestTags.SUBSCRIPTION),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
        ) {
            Text("Subscribers only", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                text = message ?: "Lists require an active subscription.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ListsScreenPreview() {
    InterlinedListTheme {
        ListsScreen(
            state = ListsUiState(
                lists = listOf(
                    ListSummary("1", "Reading list", "Books to read", 12, null, false, null),
                    ListSummary("2", "Restaurants", null, 4, null, true, null),
                ),
            ),
            onOpenList = {},
            onSearchQueryChange = {},
            onLoadMore = {},
            onCreateList = {},
        )
    }
}
