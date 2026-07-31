package com.interlinedlist.android.feature.lists.ui.folders

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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.interlinedlist.android.feature.lists.domain.ListFolder

/** Stable test tags for the folder browser. */
object FolderBrowserTestTags {
    const val LIST = "folderList"
    const val EMPTY = "folderEmpty"
    const val PROGRESS = "folderProgress"
    const val ERROR = "folderError"
    const val RENAME_DIALOG = "folderRenameDialog"
    const val RENAME_FIELD = "folderRenameField"
    const val RENAME_CONFIRM = "folderRenameConfirm"
    const val DELETE_DIALOG = "folderDeleteDialog"
    const val DELETE_CONFIRM = "folderDeleteConfirm"
    fun folder(id: String) = "folder_$id"
    fun overflow(id: String) = "folderOverflow_$id"
}

/**
 * Hilt-wired entry for the folder browser. [onBack] pops navigation. Folders can be
 * renamed, moved under another folder (or to the root), and deleted (with a confirm
 * dialog) via each row's overflow menu.
 */
@Composable
fun FolderBrowserRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FolderBrowserViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    FolderBrowserScreen(
        state = state,
        onBack = onBack,
        onRename = viewModel::renameFolder,
        onMove = viewModel::moveFolder,
        onDelete = viewModel::deleteFolder,
        modifier = modifier,
    )
}

/** Stateless folder browser — list folders, rename/move/delete each. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderBrowserScreen(
    state: FolderBrowserUiState,
    onBack: () -> Unit,
    onRename: (ListFolder, String) -> Unit,
    onMove: (ListFolder, String?) -> Unit,
    onDelete: (ListFolder) -> Unit,
    modifier: Modifier = Modifier,
) {
    var renaming by remember { mutableStateOf<ListFolder?>(null) }
    var deleting by remember { mutableStateOf<ListFolder?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Folders") },
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
            ) { CircularProgressIndicator(Modifier.testTag(FolderBrowserTestTags.PROGRESS)) }

            state.isEmpty -> Box(
                Modifier.fillMaxSize().padding(padding).testTag(FolderBrowserTestTags.EMPTY),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No folders yet", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Create a folder to organise your lists.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            else -> Column(Modifier.padding(padding)) {
                if (state.errorMessage != null) {
                    Text(
                        text = state.errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .testTag(FolderBrowserTestTags.ERROR),
                    )
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(FolderBrowserTestTags.LIST),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.folders, key = { it.id }) { folder ->
                        FolderRow(
                            folder = folder,
                            others = state.folders.filterNot { it.id == folder.id },
                            onRename = { renaming = folder },
                            onMove = { onMove(folder, it) },
                            onDelete = { deleting = folder },
                        )
                    }
                }
            }
        }
    }

    renaming?.let { folder ->
        RenameFolderDialog(
            folder = folder,
            onConfirm = { newName ->
                onRename(folder, newName)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }

    deleting?.let { folder ->
        DeleteFolderDialog(
            folder = folder,
            onConfirm = {
                onDelete(folder)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderRow(
    folder: ListFolder,
    others: List<ListFolder>,
    onRename: () -> Unit,
    onMove: (String?) -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(FolderBrowserTestTags.folder(folder.id)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Folder, contentDescription = null)
            Text(
                text = folder.name.ifBlank { "Untitled folder" },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            )
            IconButton(
                onClick = { menuOpen = true },
                modifier = Modifier.testTag(FolderBrowserTestTags.overflow(folder.id)),
            ) { Icon(Icons.Default.MoreVert, contentDescription = "Folder actions") }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = { menuOpen = false; onRename() },
                )
                if (folder.parentId != null) {
                    DropdownMenuItem(
                        text = { Text("Move to root") },
                        onClick = { menuOpen = false; onMove(null) },
                    )
                }
                others.forEach { target ->
                    DropdownMenuItem(
                        text = { Text("Move to \"${target.name}\"") },
                        onClick = { menuOpen = false; onMove(target.id) },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }
}

@Composable
private fun RenameFolderDialog(
    folder: ListFolder,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(folder.id) { mutableStateOf(folder.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(FolderBrowserTestTags.RENAME_DIALOG),
        title = { Text("Rename folder") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                isError = name.isBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(FolderBrowserTestTags.RENAME_FIELD),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag(FolderBrowserTestTags.RENAME_CONFIRM),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DeleteFolderDialog(
    folder: ListFolder,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(FolderBrowserTestTags.DELETE_DIALOG),
        title = { Text("Delete folder?") },
        text = {
            Text("\"${folder.name}\" will be deleted. Its lists move to the root; they are not deleted.")
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag(FolderBrowserTestTags.DELETE_CONFIRM),
            ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Preview(showBackground = true)
@Composable
private fun FolderBrowserScreenPreview() {
    InterlinedListTheme {
        FolderBrowserScreen(
            state = FolderBrowserUiState(
                folders = listOf(
                    ListFolder("f1", "Work", null),
                    ListFolder("f2", "Personal", null),
                ),
                isLoading = false,
            ),
            onBack = {},
            onRename = { _, _ -> },
            onMove = { _, _ -> },
            onDelete = {},
        )
    }
}
