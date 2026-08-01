package com.interlinedlist.android.feature.documents.ui.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.interlinedlist.android.feature.documents.domain.Document
import com.interlinedlist.android.feature.documents.domain.FolderNode
import com.interlinedlist.android.feature.documents.domain.FolderSummary

/** The modal currently shown over the browser (single source of truth for dialogs). */
sealed interface BrowserDialog {
    data object None : BrowserDialog
    data object CreateDocument : BrowserDialog
    data object CreateFolder : BrowserDialog
    data class RenameFolder(val folder: FolderSummary) : BrowserDialog
    data class ConfirmDeleteFolder(val folder: FolderSummary) : BrowserDialog
    data class MoveDocument(val document: Document) : BrowserDialog
    data class ConfirmDeleteDoc(val document: Document) : BrowserDialog
}

@Composable
fun BrowserDialogs(
    dialog: BrowserDialog,
    state: DocumentsBrowserUiState,
    onCreateDocument: (String) -> Unit,
    onCreateFolder: (String) -> Unit,
    onRenameFolder: (String, String) -> Unit,
    onDeleteFolder: (String) -> Unit,
    onMoveDocument: (String, String?) -> Unit,
    onDeleteDocument: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    when (dialog) {
        BrowserDialog.None -> Unit

        BrowserDialog.CreateDocument -> TextInputDialog(
            title = "New document",
            label = "Title",
            confirmLabel = "Create",
            initial = "",
            onConfirm = { onCreateDocument(it); onDismiss() },
            onDismiss = onDismiss,
        )

        BrowserDialog.CreateFolder -> TextInputDialog(
            title = "New folder",
            label = "Folder name",
            confirmLabel = "Create",
            initial = "",
            requireNonBlank = true,
            onConfirm = { onCreateFolder(it); onDismiss() },
            onDismiss = onDismiss,
        )

        is BrowserDialog.RenameFolder -> TextInputDialog(
            title = "Rename folder",
            label = "Folder name",
            confirmLabel = "Rename",
            initial = dialog.folder.name,
            requireNonBlank = true,
            onConfirm = { onRenameFolder(dialog.folder.id, it); onDismiss() },
            onDismiss = onDismiss,
        )

        is BrowserDialog.ConfirmDeleteFolder -> ConfirmDialog(
            title = "Delete folder?",
            message = "\"${dialog.folder.name}\" and everything inside it will be deleted.",
            confirmLabel = "Delete",
            onConfirm = { onDeleteFolder(dialog.folder.id); onDismiss() },
            onDismiss = onDismiss,
        )

        is BrowserDialog.ConfirmDeleteDoc -> ConfirmDialog(
            title = "Delete document?",
            message = "\"${dialog.document.title}\" will be deleted.",
            confirmLabel = "Delete",
            onConfirm = { onDeleteDocument(dialog.document.id); onDismiss() },
            onDismiss = onDismiss,
        )

        is BrowserDialog.MoveDocument -> MoveDocumentDialog(
            document = dialog.document,
            folders = state.allFolders,
            onMove = { target -> onMoveDocument(dialog.document.id, target); onDismiss() },
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun TextInputDialog(
    title: String,
    label: String,
    confirmLabel: String,
    initial: String,
    requireNonBlank: Boolean = false,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("dialogInput"),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value) },
                enabled = !requireNonBlank || value.isNotBlank(),
                modifier = Modifier.testTag("dialogConfirm"),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("dialogConfirm")) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Lets the user pick a destination folder (or the root) for a document. */
@Composable
private fun MoveDocumentDialog(
    document: Document,
    folders: List<FolderSummary>,
    onMove: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move \"${document.title}\"") },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                item {
                    FolderPickerRow(
                        name = FolderNode.ROOT_NAME,
                        selected = document.folderId == null,
                        onClick = { onMove(null) },
                    )
                }
                items(folders, key = { it.id }) { folder ->
                    FolderPickerRow(
                        name = folder.name,
                        selected = document.folderId == folder.id,
                        onClick = { onMove(folder.id) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun FolderPickerRow(name: String, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !selected, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.padding(horizontal = 8.dp))
        Text(
            text = name + if (selected) "  (current)" else "",
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Full-screen search overlay backed by the dedicated `/documents/search` endpoint. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentSearchOverlay(
    query: String,
    isSearching: Boolean,
    results: List<Document>,
    onQueryChange: (String) -> Unit,
    onOpenDocument: (String) -> Unit,
    onClose: () -> Unit,
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            placeholder = { Text("Search documents") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag(DocumentsBrowserTestTags.SEARCH_FIELD),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, contentDescription = "Close search")
                        }
                    },
                )
                when {
                    isSearching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                    query.isBlank() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Type to search your documents.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "No documents match \"$query\".",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize().testTag(DocumentsBrowserTestTags.SEARCH_RESULTS),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        items(results, key = { it.id }) { doc ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenDocument(doc.id) }
                                    .testTag(DocumentsBrowserTestTags.docRow(doc.id))
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                Text(doc.title, style = MaterialTheme.typography.titleMedium)
                                if (doc.snippet.isNotBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        doc.snippet,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
