package com.interlinedlist.android.feature.documents.ui.index

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.documents.domain.Document
import com.interlinedlist.android.feature.documents.domain.DocumentFolder

/** Stable test tags for the documents index. */
object DocumentsTestTags {
    const val LIST = "documentsList"
    const val CREATE_FAB = "documentsCreateFab"
    const val SEARCH = "documentsSearch"
    const val EMPTY = "documentsEmpty"
    const val PROGRESS = "documentsProgress"
    const val ERROR = "documentsError"
    const val SUBSCRIPTION_GATE = "documentsSubscriptionGate"
    fun row(id: String) = "documentRow_$id"
    fun folderChip(id: String?) = "folderChip_${id ?: "root"}"
}

/**
 * Hilt-wired index entry. [onOpenDocument] navigates to the editor for a document
 * id; [onCreateDocument] is invoked with the id of a freshly created document so
 * the caller can open it; [onSearch] opens the search screen.
 */
@Composable
fun DocumentsRoute(
    onOpenDocument: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DocumentsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DocumentsScreen(
        state = state,
        onSelectFolder = viewModel::selectFolder,
        onOpenDocument = onOpenDocument,
        onCreateDocument = { viewModel.createDocument(title = "Untitled", onCreated = onOpenDocument) },
        onLoadMore = viewModel::loadMore,
        onSearch = onSearch,
        modifier = modifier,
    )
}

/** Stateless documents index — folder filter chips + a scrollable list. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(
    state: DocumentsUiState,
    onSelectFolder: (String?) -> Unit,
    onOpenDocument: (String) -> Unit,
    onCreateDocument: () -> Unit,
    onLoadMore: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // Trigger load-more when the last item scrolls into view.
    LaunchedEffect(listState, state.hasMore, state.documents.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisible ->
                if (state.hasMore && !state.isLoadingMore &&
                    lastVisible != null && lastVisible >= state.documents.size - 1
                ) {
                    onLoadMore()
                }
            }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Documents") },
                actions = {
                    IconButton(onClick = onSearch, modifier = Modifier.testTag(DocumentsTestTags.SEARCH)) {
                        Icon(Icons.Default.Search, contentDescription = "Search documents")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateDocument,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New") },
                modifier = Modifier.testTag(DocumentsTestTags.CREATE_FAB),
            )
        },
    ) { padding ->
        when {
            state.subscriptionRequired -> SubscriptionGate(
                message = state.errorMessage,
                modifier = Modifier.padding(padding),
            )

            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(Modifier.testTag(DocumentsTestTags.PROGRESS))
            }

            else -> DocumentsContent(
                state = state,
                listState = listState,
                onSelectFolder = onSelectFolder,
                onOpenDocument = onOpenDocument,
                contentPadding = padding,
            )
        }
    }
}

@Composable
private fun DocumentsContent(
    state: DocumentsUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onSelectFolder: (String?) -> Unit,
    onOpenDocument: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        if (state.folders.isNotEmpty()) {
            FolderChips(
                folders = state.folders,
                selectedFolderId = state.selectedFolderId,
                onSelectFolder = onSelectFolder,
            )
        }

        if (state.errorMessage != null && !state.subscriptionRequired) {
            Text(
                text = state.errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag(DocumentsTestTags.ERROR),
            )
        }

        if (state.isEmpty) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No documents yet. Tap New to create one.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(DocumentsTestTags.EMPTY),
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().testTag(DocumentsTestTags.LIST),
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                items(state.documents, key = { it.id }) { document ->
                    DocumentRow(document = document, onClick = { onOpenDocument(document.id) })
                }
                if (state.isLoadingMore) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderChips(
    folders: List<DocumentFolder>,
    selectedFolderId: String?,
    onSelectFolder: (String?) -> Unit,
) {
    androidx.compose.foundation.lazy.LazyRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        item {
            FilterChip(
                selected = selectedFolderId == null,
                onClick = { onSelectFolder(null) },
                label = { Text("All") },
                modifier = Modifier.testTag(DocumentsTestTags.folderChip(null)),
            )
        }
        items(folders, key = { it.id }) { folder ->
            FilterChip(
                selected = selectedFolderId == folder.id,
                onClick = { onSelectFolder(folder.id) },
                label = { Text(folder.name) },
                modifier = Modifier.testTag(DocumentsTestTags.folderChip(folder.id)),
            )
        }
    }
}

@Composable
private fun DocumentRow(document: Document, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(DocumentsTestTags.row(document.id))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = document.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (document.snippet.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = document.snippet,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (document.folderName != null || document.updatedAt != null) {
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                document.folderName?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                document.updatedAt?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SubscriptionGate(message: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().padding(24.dp).testTag(DocumentsTestTags.SUBSCRIPTION_GATE),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Subscriber feature",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message ?: "Documents require an active subscription.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DocumentsScreenPreview() {
    InterlinedListTheme {
        DocumentsScreen(
            state = DocumentsUiState(
                documents = listOf(
                    Document("1", "Grocery list", null, "Milk, eggs, bread", null, null, false, "2h ago"),
                    Document("2", "Meeting notes", null, "Discussed Q3 roadmap", "f1", "Work", false, "1d ago"),
                ),
                folders = listOf(DocumentFolder("f1", "Work", null)),
            ),
            onSelectFolder = {},
            onOpenDocument = {},
            onCreateDocument = {},
            onLoadMore = {},
            onSearch = {},
        )
    }
}
