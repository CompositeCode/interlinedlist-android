package com.interlinedlist.android.feature.documents.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import com.interlinedlist.android.feature.documents.domain.Presence
import com.interlinedlist.android.feature.documents.ui.common.MarkdownText
import com.interlinedlist.android.feature.documents.ui.presence.PresenceIndicator

/** Stable test tags for the editor. */
object DocumentEditorTestTags {
    const val TITLE = "editorTitle"
    const val BODY = "editorBody"
    const val PREVIEW = "editorPreview"
    const val SAVE = "editorSave"
    const val DELETE = "editorDelete"
    const val UPLOAD_IMAGE = "editorUploadImage"
    const val SHARE = "editorShare"
    const val MANAGE_ACCESS = "editorManageAccess"
    const val TOGGLE_PREVIEW = "editorTogglePreview"
    const val PROGRESS = "editorProgress"
    const val ERROR = "editorError"
    const val CONFLICT_BANNER = "editorConflictBanner"
    const val CONFLICT_RELOAD = "editorConflictReload"
    const val CONFLICT_RETRY = "editorConflictRetry"
    const val OFFLINE_HINT = "editorOfflineHint"
}

/**
 * Hilt-wired editor entry. Reads its target document id from the `documentId` nav
 * arg via SavedStateHandle. [onBack] navigates up; [onDeleted] navigates back to
 * the index after a successful delete.
 */
@Composable
fun DocumentEditorRoute(
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenShare: () -> Unit = {},
    onOpenManageAccess: () -> Unit = {},
    presenceViewModel: com.interlinedlist.android.feature.documents.ui.presence.DocumentPresenceViewModel = hiltViewModel(),
    viewModel: DocumentEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val presenceState by presenceViewModel.uiState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Heartbeat presence while the editor is on screen; leave when it disappears.
    androidx.compose.runtime.DisposableEffect(Unit) {
        presenceViewModel.start()
        onDispose { presenceViewModel.stop() }
    }

    // Android Photo Picker: reads the picked image's bytes and hands them to the VM.
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            val resolver = context.contentResolver
            val mime = resolver.getType(uri) ?: "image/*"
            val bytes = runCatching {
                resolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
            if (bytes != null) {
                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "image"
                viewModel.uploadImage(fileName = name, mimeType = mime, bytes = bytes)
            }
        }
    }

    DocumentEditorScreen(
        state = state,
        onTitleChange = viewModel::onTitleChange,
        onContentChange = viewModel::onContentChange,
        onTogglePreview = viewModel::togglePreview,
        onSave = { viewModel.save() },
        onDelete = { viewModel.delete(onDeleted) },
        onPickImage = {
            pickImage.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        },
        onBack = onBack,
        onOpenShare = onOpenShare,
        onOpenManageAccess = onOpenManageAccess,
        onReloadConflict = viewModel::reloadForConflict,
        onRetrySave = { viewModel.save() },
        presenceParticipants = presenceState.participants,
        modifier = modifier,
    )
}

/** Stateless editor: a title field, a markdown body field, and a preview toggle. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DocumentEditorScreen(
    state: DocumentEditorUiState,
    onTitleChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onTogglePreview: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onPickImage: () -> Unit = {},
    onOpenShare: () -> Unit = {},
    onOpenManageAccess: () -> Unit = {},
    onReloadConflict: () -> Unit = {},
    onRetrySave: () -> Unit = {},
    presenceParticipants: List<Presence> = emptyList(),
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.title.ifBlank { "Untitled" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    PresenceIndicator(
                        participants = presenceParticipants,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    IconButton(
                        onClick = onPickImage,
                        enabled = !state.isUploadingImage && !state.isSaving,
                        modifier = Modifier.testTag(DocumentEditorTestTags.UPLOAD_IMAGE),
                    ) {
                        if (state.isUploadingImage) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Image, contentDescription = "Insert image")
                        }
                    }
                    IconButton(
                        onClick = onOpenManageAccess,
                        modifier = Modifier.testTag(DocumentEditorTestTags.MANAGE_ACCESS),
                    ) {
                        Icon(Icons.Outlined.Group, contentDescription = "Manage access")
                    }
                    IconButton(
                        onClick = onOpenShare,
                        modifier = Modifier.testTag(DocumentEditorTestTags.SHARE),
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = "Share")
                    }
                    IconButton(
                        onClick = onTogglePreview,
                        modifier = Modifier.testTag(DocumentEditorTestTags.TOGGLE_PREVIEW),
                    ) {
                        if (state.isPreview) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        } else {
                            Icon(Icons.Default.Visibility, contentDescription = "Preview")
                        }
                    }
                    IconButton(
                        onClick = onDelete,
                        enabled = !state.isSaving,
                        modifier = Modifier.testTag(DocumentEditorTestTags.DELETE),
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                    TextButton(
                        onClick = onSave,
                        enabled = state.canSave,
                        modifier = Modifier.testTag(DocumentEditorTestTags.SAVE),
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                Modifier.size(18.dp).testTag(DocumentEditorTestTags.PROGRESS),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Save")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading && state.content.isBlank() && state.title.isBlank()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = 16.dp),
        ) {
            if (state.hasConflict) {
                ConflictBanner(
                    message = state.errorMessage
                        ?: "This document changed since you opened it.",
                    onReload = onReloadConflict,
                    onRetry = onRetrySave,
                )
            } else if (state.errorMessage != null) {
                Text(
                    text = state.errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .testTag(DocumentEditorTestTags.ERROR),
                )
            }

            if (state.isQueuedOffline) {
                Text(
                    text = "Saved offline. Changes will sync when you're back online.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .testTag(DocumentEditorTestTags.OFFLINE_HINT),
                )
            }

            OutlinedTextField(
                value = state.title,
                onValueChange = onTitleChange,
                label = { Text("Title") },
                singleLine = true,
                enabled = !state.isPreview,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .testTag(DocumentEditorTestTags.TITLE),
            )

            if (state.isPreview) {
                MarkdownText(
                    markdown = state.content.ifBlank { "_Nothing to preview yet._" },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 8.dp)
                        .testTag(DocumentEditorTestTags.PREVIEW),
                )
            } else {
                OutlinedTextField(
                    value = state.content,
                    onValueChange = onContentChange,
                    label = { Text("Markdown") },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 8.dp)
                        .verticalScroll(rememberScrollState())
                        .testTag(DocumentEditorTestTags.BODY),
                )
            }
        }
    }
}

/** A save-conflict banner offering Reload (take server copy) or Retry (overwrite). */
@Composable
private fun ConflictBanner(
    message: String,
    onReload: () -> Unit,
    onRetry: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag(DocumentEditorTestTags.CONFLICT_BANNER),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onReload,
                    modifier = Modifier.testTag(DocumentEditorTestTags.CONFLICT_RELOAD),
                ) { Text("Reload latest") }
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier.testTag(DocumentEditorTestTags.CONFLICT_RETRY),
                ) { Text("Retry") }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DocumentEditorScreenPreview() {
    InterlinedListTheme {
        DocumentEditorScreen(
            state = DocumentEditorUiState(
                documentId = "1",
                title = "Meeting notes",
                content = "# Agenda\n- Roadmap\n- **Budget**\n\nMore details here.",
                isLoading = false,
                hasUnsavedChanges = true,
            ),
            onTitleChange = {},
            onContentChange = {},
            onTogglePreview = {},
            onSave = {},
            onDelete = {},
            onBack = {},
        )
    }
}
