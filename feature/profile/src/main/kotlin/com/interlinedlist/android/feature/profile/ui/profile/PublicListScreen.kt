package com.interlinedlist.android.feature.profile.ui.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.profile.domain.PublicListCell
import com.interlinedlist.android.feature.profile.domain.PublicListDetail
import com.interlinedlist.android.feature.profile.domain.PublicListRow

/** Stable test tags for the read-only public list view. */
object PublicListTestTags {
    const val TITLE = "publicListTitle"
    const val LIST = "publicListRows"
    const val EMPTY = "publicListEmpty"
    const val PROGRESS = "publicListProgress"
    const val ERROR = "publicListError"
    const val BACK = "publicListBack"
    fun row(id: String) = "publicListRow_$id"
}

/**
 * A read-only view of another user's public list (route `publicList/{username}/{listId}`).
 *
 * @param onBack pop back to the profile.
 */
@Composable
fun PublicListRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PublicListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PublicListScreen(state = state, onBack = onBack, onRetry = viewModel::refresh, modifier = modifier)
}

/** Stateless read-only list UI. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PublicListScreen(
    state: PublicListUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.list?.displayTitle ?: "List",
                        modifier = Modifier.testTag(PublicListTestTags.TITLE),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(PublicListTestTags.BACK)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading && state.list == null -> CircularProgressIndicator(
                    Modifier.align(Alignment.Center).testTag(PublicListTestTags.PROGRESS),
                )

                state.list == null -> Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = state.errorMessage ?: "Couldn't load this list.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag(PublicListTestTags.ERROR),
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRetry) { Text("Retry") }
                }

                state.list.rows.isEmpty() -> Text(
                    text = "This list has no rows.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).testTag(PublicListTestTags.EMPTY),
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag(PublicListTestTags.LIST),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                ) {
                    state.list.description?.takeIf { it.isNotBlank() }?.let { description ->
                        item {
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            )
                        }
                    }
                    items(state.list.rows, key = { it.id }) { row ->
                        PublicListRowCard(row)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PublicListRowCard(row: PublicListRow) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag(PublicListTestTags.row(row.id)),
    ) {
        Column(Modifier.padding(12.dp)) {
            row.cells.forEach { cell ->
                Text(
                    text = "${cell.label}: ${cell.value}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PublicListScreenPreview() {
    InterlinedListTheme {
        PublicListScreen(
            state = PublicListUiState(
                list = PublicListDetail(
                    id = "l1",
                    title = "Todos",
                    description = "My todo list",
                    rows = listOf(
                        PublicListRow(
                            id = "r1",
                            cells = listOf(
                                PublicListCell("task", "Ship it"),
                                PublicListCell("done", "false"),
                            ),
                        ),
                    ),
                ),
                isLoading = false,
            ),
            onBack = {},
            onRetry = {},
        )
    }
}
