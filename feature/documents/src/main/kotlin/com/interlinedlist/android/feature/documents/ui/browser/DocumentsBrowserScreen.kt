package com.interlinedlist.android.feature.documents.ui.browser

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.interlinedlist.android.feature.documents.domain.FolderContents
import com.interlinedlist.android.feature.documents.domain.FolderNode
import com.interlinedlist.android.feature.documents.domain.FolderSummary

/** Stable test tags for the documents browser. */
object DocumentsBrowserTestTags {
    const val LIST = "browserList"
    const val CREATE_FAB = "browserCreateFab"
    const val CREATE_DOC = "browserCreateDoc"
    const val CREATE_FOLDER = "browserCreateFolder"
    const val SEARCH_ACTION = "browserSearchAction"
    const val SEARCH_FIELD = "browserSearchField"
    const val SEARCH_RESULTS = "browserSearchResults"
    const val BREADCRUMB = "browserBreadcrumb"
    const val EMPTY = "browserEmpty"
    const val PROGRESS = "browserProgress"
    const val ERROR = "browserError"
    const val SUBSCRIPTION_GATE = "browserSubscriptionGate"
    const val BACK = "browserBack"
    fun folderRow(id: String) = "folderRow_$id"
    fun docRow(id: String) = "docRow_$id"
    fun crumb(id: String) = "crumb_$id"
}

/**
 * Root browser entry (the app's Documents tab). Kept named `DocumentsRoute` so the
 * app NavHost keeps compiling. [onOpenFolder] pushes a drill-down level;
 * [onOpenDocument] opens the editor; [onCreateDocument] receives the id of a freshly
 * created document so the caller can open it.
 */
@Composable
fun DocumentsRoute(
    onOpenFolder: (String) -> Unit,
    onOpenDocument: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DocumentsBrowserViewModel = hiltViewModel(),
) {
    DocumentsFolderRoute(
        onOpenFolder = onOpenFolder,
        onOpenDocument = onOpenDocument,
        onBack = null,
        modifier = modifier,
        viewModel = viewModel,
    )
}

/**
 * A single drill-down level. Reads its target folder from [FOLDER_ID_ARG] via the
 * ViewModel's SavedStateHandle. [onBack] is null at the root (no up navigation).
 */
@Composable
fun DocumentsFolderRoute(
    onOpenFolder: (String) -> Unit,
    onOpenDocument: (String) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    viewModel: DocumentsBrowserViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DocumentsBrowserScreen(
        state = state,
        onOpenFolder = onOpenFolder,
        onOpenDocument = onOpenDocument,
        onCreateDocument = { title -> viewModel.createDocument(title, onOpenDocument) },
        onCreateFolder = viewModel::createFolder,
        onRenameFolder = viewModel::renameFolder,
        onDeleteFolder = viewModel::deleteFolder,
        onMoveDocument = viewModel::moveDocument,
        onDeleteDocument = viewModel::deleteDocument,
        onOpenSearch = viewModel::openSearch,
        onCloseSearch = viewModel::closeSearch,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsBrowserScreen(
    state: DocumentsBrowserUiState,
    onOpenFolder: (String) -> Unit,
    onOpenDocument: (String) -> Unit,
    onCreateDocument: (String) -> Unit,
    onCreateFolder: (String) -> Unit,
    onRenameFolder: (String, String) -> Unit,
    onDeleteFolder: (String) -> Unit,
    onMoveDocument: (String, String?) -> Unit,
    onDeleteDocument: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var dialog by remember { mutableStateOf<BrowserDialog>(BrowserDialog.None) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.contents.folderName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack, modifier = Modifier.testTag(DocumentsBrowserTestTags.BACK)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up")
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onOpenSearch,
                        modifier = Modifier.testTag(DocumentsBrowserTestTags.SEARCH_ACTION),
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Search documents")
                    }
                    IconButton(
                        onClick = { dialog = BrowserDialog.CreateFolder },
                        modifier = Modifier.testTag(DocumentsBrowserTestTags.CREATE_FOLDER),
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "New folder")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { dialog = BrowserDialog.CreateDocument },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New") },
                modifier = Modifier.testTag(DocumentsBrowserTestTags.CREATE_FAB),
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
                CircularProgressIndicator(Modifier.testTag(DocumentsBrowserTestTags.PROGRESS))
            }

            else -> BrowserContent(
                state = state,
                onOpenFolder = onOpenFolder,
                onOpenDocument = onOpenDocument,
                onRequestRename = { dialog = BrowserDialog.RenameFolder(it) },
                onRequestDeleteFolder = { dialog = BrowserDialog.ConfirmDeleteFolder(it) },
                onRequestMoveDoc = { dialog = BrowserDialog.MoveDocument(it) },
                onRequestDeleteDoc = { dialog = BrowserDialog.ConfirmDeleteDoc(it) },
                contentPadding = padding,
            )
        }
    }

    if (state.isSearchActive) {
        DocumentSearchOverlay(
            query = state.searchQuery,
            isSearching = state.isSearching,
            results = state.searchResults,
            onQueryChange = onSearchQueryChange,
            onOpenDocument = { onOpenDocument(it); onCloseSearch() },
            onClose = onCloseSearch,
        )
    }

    BrowserDialogs(
        dialog = dialog,
        state = state,
        onCreateDocument = onCreateDocument,
        onCreateFolder = onCreateFolder,
        onRenameFolder = onRenameFolder,
        onDeleteFolder = onDeleteFolder,
        onMoveDocument = onMoveDocument,
        onDeleteDocument = onDeleteDocument,
        onDismiss = { dialog = BrowserDialog.None },
    )
}

@Composable
private fun BrowserContent(
    state: DocumentsBrowserUiState,
    onOpenFolder: (String) -> Unit,
    onOpenDocument: (String) -> Unit,
    onRequestRename: (FolderSummary) -> Unit,
    onRequestDeleteFolder: (FolderSummary) -> Unit,
    onRequestMoveDoc: (Document) -> Unit,
    onRequestDeleteDoc: (Document) -> Unit,
    contentPadding: PaddingValues,
) {
    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        if (state.contents.breadcrumb.size > 1) {
            Breadcrumb(state.contents.breadcrumb, onOpenFolder)
        }

        if (state.errorMessage != null && !state.subscriptionRequired) {
            Text(
                text = state.errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag(DocumentsBrowserTestTags.ERROR),
            )
        }

        if (state.isEmpty) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "This folder is empty. Tap New to add a document or folder.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp).testTag(DocumentsBrowserTestTags.EMPTY),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag(DocumentsBrowserTestTags.LIST),
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                items(state.contents.subfolders, key = { "folder-${it.id}" }) { folder ->
                    FolderRow(
                        folder = folder,
                        onClick = { onOpenFolder(folder.id) },
                        onRename = { onRequestRename(folder) },
                        onDelete = { onRequestDeleteFolder(folder) },
                    )
                }
                items(state.contents.documents, key = { "doc-${it.id}" }) { document ->
                    DocumentRow(
                        document = document,
                        onClick = { onOpenDocument(document.id) },
                        onMove = { onRequestMoveDoc(document) },
                        onDelete = { onRequestDeleteDoc(document) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Breadcrumb(path: List<FolderSummary>, onOpenFolder: (String) -> Unit) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag(DocumentsBrowserTestTags.BREADCRUMB),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(path, key = { it.id }) { crumb ->
            val isLast = crumb.id == path.last().id
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = crumb.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isLast) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    modifier = Modifier
                        .clickable(enabled = !isLast) { onOpenFolder(crumb.id) }
                        .padding(vertical = 4.dp, horizontal = 2.dp)
                        .testTag(DocumentsBrowserTestTags.crumb(crumb.id)),
                )
                if (!isLast) {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderRow(
    folder: FolderSummary,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(DocumentsBrowserTestTags.folderRow(folder.id))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(folder.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = subtitleFor(folder),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RowOverflowMenu(
            actions = listOf(
                OverflowAction("Rename", Icons.Default.Edit, onRename),
                OverflowAction("Delete", Icons.Default.Delete, onDelete),
            ),
        )
    }
}

@Composable
private fun DocumentRow(
    document: Document,
    onClick: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(DocumentsBrowserTestTags.docRow(document.id))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(document.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (document.snippet.isNotBlank()) {
                Text(
                    text = document.snippet,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        RowOverflowMenu(
            actions = listOf(
                OverflowAction("Move", Icons.AutoMirrored.Filled.DriveFileMove, onMove),
                OverflowAction("Delete", Icons.Default.Delete, onDelete),
            ),
        )
    }
}

private data class OverflowAction(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val onClick: () -> Unit)

@Composable
private fun RowOverflowMenu(actions: List<OverflowAction>) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "More actions")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    leadingIcon = { Icon(action.icon, contentDescription = null) },
                    onClick = { expanded = false; action.onClick() },
                )
            }
        }
    }
}

private fun subtitleFor(folder: FolderSummary): String {
    val parts = buildList {
        if (folder.subfolderCount > 0) add("${folder.subfolderCount} folder" + if (folder.subfolderCount == 1) "" else "s")
        add("${folder.documentCount} doc" + if (folder.documentCount == 1) "" else "s")
    }
    return parts.joinToString(" · ")
}

@Composable
private fun SubscriptionGate(message: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().padding(24.dp).testTag(DocumentsBrowserTestTags.SUBSCRIPTION_GATE),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Subscriber feature", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
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
private fun DocumentsBrowserPreview() {
    InterlinedListTheme {
        DocumentsBrowserScreen(
            state = DocumentsBrowserUiState(
                isLoading = false,
                contents = FolderContents(
                    folderId = FolderNode.ROOT_ID,
                    folderName = "Documents",
                    parentId = null,
                    subfolders = listOf(FolderSummary("f1", "Work", 3, 1), FolderSummary("f2", "Personal", 1, 0)),
                    documents = listOf(Document("1", "Grocery list", null, "Milk, eggs, bread", null, null, false, null)),
                    breadcrumb = listOf(FolderSummary(FolderNode.ROOT_ID, "Documents")),
                ),
            ),
            onOpenFolder = {},
            onOpenDocument = {},
            onCreateDocument = {},
            onCreateFolder = {},
            onRenameFolder = { _, _ -> },
            onDeleteFolder = {},
            onMoveDocument = { _, _ -> },
            onDeleteDocument = {},
            onOpenSearch = {},
            onCloseSearch = {},
            onSearchQueryChange = {},
            onBack = null,
        )
    }
}
