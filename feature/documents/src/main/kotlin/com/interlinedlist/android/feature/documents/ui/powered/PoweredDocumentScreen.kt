package com.interlinedlist.android.feature.documents.ui.powered

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.interlinedlist.android.feature.documents.domain.ListSource
import com.interlinedlist.android.feature.documents.ui.browser.DocumentSearchOverlay
import com.interlinedlist.android.feature.documents.ui.common.MarkdownText

/** Stable test tags for the Powered Document surface. */
object PoweredDocumentTestTags {
    const val FORM = "poweredDocForm"
    const val INSTRUCTION = "poweredDocInstruction"
    const val URL_FIELD = "poweredDocUrl"
    const val PICK_LIST = "poweredDocPickList"
    const val PICK_DOCUMENT = "poweredDocPickDocument"
    const val SUGGEST = "poweredDocSuggest"
    const val PREVIEW = "poweredDocPreview"
    const val PREVIEW_TITLE = "poweredDocPreviewTitle"
    const val SAVE = "poweredDocSave"
    const val DISCARD = "poweredDocDiscard"
    const val PROGRESS = "poweredDocProgress"
    const val ERROR = "poweredDocError"
    const val QUOTA = "poweredDocQuota"
    const val UNAVAILABLE = "poweredDocUnavailable"
    fun modeChip(mode: PoweredDocumentMode) = "poweredDocMode_${mode.apiValue}"
    fun listRow(id: String) = "poweredDocListRow_$id"
}

/**
 * Hilt-wired Powered Document route. [onOpenDocument] receives the id of the
 * document `/generate` created, so the caller can open it in the editor.
 */
@Composable
fun PoweredDocumentRoute(
    onOpenDocument: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PoweredDocumentViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.createdDocumentId) {
        state.createdDocumentId?.let(onOpenDocument)
    }

    PoweredDocumentScreen(
        state = state,
        onSelectMode = viewModel::selectMode,
        onInstructionChange = viewModel::onInstructionChange,
        onResearchUrlChange = viewModel::onResearchUrlChange,
        onOpenListPicker = viewModel::openListPicker,
        onSelectList = viewModel::selectList,
        onOpenDocumentPicker = viewModel::openDocumentPicker,
        onDocumentQueryChange = viewModel::onDocumentQueryChange,
        onSelectDocument = viewModel::selectDocument,
        onClosePicker = viewModel::closePicker,
        onSuggest = viewModel::suggest,
        onEditedTitleChange = viewModel::onEditedTitleChange,
        onConfirm = viewModel::confirm,
        onDiscard = viewModel::discard,
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoweredDocumentScreen(
    state: PoweredDocumentUiState,
    onSelectMode: (PoweredDocumentMode) -> Unit,
    onInstructionChange: (String) -> Unit,
    onResearchUrlChange: (String) -> Unit,
    onOpenListPicker: () -> Unit,
    onSelectList: (ListSource) -> Unit,
    onOpenDocumentPicker: () -> Unit,
    onDocumentQueryChange: (String) -> Unit,
    onSelectDocument: (String) -> Unit,
    onClosePicker: () -> Unit,
    onSuggest: () -> Unit,
    onEditedTitleChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDiscard: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Powered Document") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                // The entry point is already gated, but the status can lapse while
                // the screen is open; say so rather than offering a failing control.
                !state.isAiEnabled -> Unavailable()

                state.previewing != null -> DraftPreview(
                    state = state,
                    onEditedTitleChange = onEditedTitleChange,
                    onConfirm = onConfirm,
                    onDiscard = onDiscard,
                )

                else -> PoweredDocumentForm(
                    state = state,
                    onSelectMode = onSelectMode,
                    onInstructionChange = onInstructionChange,
                    onResearchUrlChange = onResearchUrlChange,
                    onOpenListPicker = onOpenListPicker,
                    onOpenDocumentPicker = onOpenDocumentPicker,
                    onSuggest = onSuggest,
                )
            }
        }
    }

    when (state.picker) {
        SourcePicker.NONE -> Unit

        SourcePicker.LISTS -> ListSourcePickerDialog(
            lists = state.listSources,
            isLoading = state.isLoadingListSources,
            onSelect = onSelectList,
            onDismiss = onClosePicker,
        )

        // The documents picker is the browser's own search overlay, reused as-is.
        SourcePicker.DOCUMENTS -> DocumentSearchOverlay(
            query = state.documentQuery,
            isSearching = state.isSearchingDocuments,
            results = state.documentResults,
            onQueryChange = onDocumentQueryChange,
            onOpenDocument = onSelectDocument,
            onClose = onClosePicker,
        )
    }
}

@Composable
private fun PoweredDocumentForm(
    state: PoweredDocumentUiState,
    onSelectMode: (PoweredDocumentMode) -> Unit,
    onInstructionChange: (String) -> Unit,
    onResearchUrlChange: (String) -> Unit,
    onOpenListPicker: () -> Unit,
    onOpenDocumentPicker: () -> Unit,
    onSuggest: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag(PoweredDocumentTestTags.FORM),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Draft a full document, then review it before anything is saved.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ModeChips(selected = state.mode, onSelectMode = onSelectMode)

        when (state.mode) {
            PoweredDocumentMode.ARTICLE -> Unit

            PoweredDocumentMode.FROM_LIST -> SourceButton(
                label = state.selectedList?.title ?: "Choose a list",
                selected = state.selectedList != null,
                onClick = onOpenListPicker,
                tag = PoweredDocumentTestTags.PICK_LIST,
            )

            PoweredDocumentMode.FROM_ARTICLE -> SourceButton(
                label = state.selectedDocument?.title ?: "Choose a document",
                selected = state.selectedDocument != null,
                onClick = onOpenDocumentPicker,
                tag = PoweredDocumentTestTags.PICK_DOCUMENT,
            )

            PoweredDocumentMode.RESEARCH_URL -> OutlinedTextField(
                value = state.researchUrl,
                onValueChange = onResearchUrlChange,
                label = { Text("Web address") },
                placeholder = { Text("https://example.com/article") },
                singleLine = true,
                isError = state.researchUrl.isNotBlank() && !ResearchUrl.isValid(state.researchUrl),
                supportingText = { Text("An http:// or https:// page to research.") },
                modifier = Modifier.fillMaxWidth().testTag(PoweredDocumentTestTags.URL_FIELD),
            )
        }

        OutlinedTextField(
            value = state.instruction,
            onValueChange = onInstructionChange,
            label = { Text(state.mode.instructionHint) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth().testTag(PoweredDocumentTestTags.INSTRUCTION),
        )

        state.errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(PoweredDocumentTestTags.ERROR),
            )
        }

        if (state.isBusy) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.testTag(PoweredDocumentTestTags.PROGRESS))
            }
        } else {
            Button(
                onClick = onSuggest,
                enabled = state.canSuggest,
                modifier = Modifier.fillMaxWidth().testTag(PoweredDocumentTestTags.SUGGEST),
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Draft a preview")
            }
        }

        QuotaNote(state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeChips(
    selected: PoweredDocumentMode,
    onSelectMode: (PoweredDocumentMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PoweredDocumentMode.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { mode ->
                    FilterChip(
                        selected = mode == selected,
                        onClick = { onSelectMode(mode) },
                        label = { Text(mode.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.testTag(PoweredDocumentTestTags.modeChip(mode)),
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceButton(label: String, selected: Boolean, onClick: () -> Unit, tag: String) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag(tag),
    ) {
        Text(
            text = if (selected) "Source: $label" else label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The draft, before anything has been written. Confirming here is the only path
 * to `/generate`; leaving is free because `/suggest` persisted nothing.
 */
@Composable
private fun DraftPreview(
    state: PoweredDocumentUiState,
    onEditedTitleChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDiscard: () -> Unit,
) {
    val preview = state.previewing ?: return
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag(PoweredDocumentTestTags.PREVIEW),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Nothing is saved yet. Review the draft, then save it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = state.editedTitle,
            onValueChange = onEditedTitleChange,
            label = { Text("Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(PoweredDocumentTestTags.PREVIEW_TITLE),
        )

        val outline = preview.artifact.documentOutline
        if (outline.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Outline", style = MaterialTheme.typography.labelLarge)
                    outline.forEach { heading ->
                        Text("• $heading", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        state.errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(PoweredDocumentTestTags.ERROR),
            )
        }

        MarkdownText(
            markdown = preview.artifact.documentMarkdown,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )

        if (state.isBusy) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.testTag(PoweredDocumentTestTags.PROGRESS))
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onDiscard,
                    modifier = Modifier.weight(1f).testTag(PoweredDocumentTestTags.DISCARD),
                ) { Text("Discard") }
                Button(
                    onClick = onConfirm,
                    enabled = state.canConfirm,
                    modifier = Modifier.weight(1f).testTag(PoweredDocumentTestTags.SAVE),
                ) { Text("Save document") }
            }
        }

        QuotaNote(state)
    }
}

@Composable
private fun QuotaNote(state: PoweredDocumentUiState) {
    val remaining = state.quota?.remainingActions ?: return
    Text(
        text = if (remaining <= 0) {
            "Daily AI limit reached. Try again tomorrow."
        } else {
            "$remaining AI actions left today."
        },
        style = MaterialTheme.typography.bodySmall,
        color = if (remaining <= 0) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.testTag(PoweredDocumentTestTags.QUOTA),
    )
}

@Composable
private fun Unavailable() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag(PoweredDocumentTestTags.UNAVAILABLE),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "AI writing assistance isn't available on this account.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Minimal in-module list picker. The lists browser lives in `:feature:lists`,
 * which this module deliberately does not depend on, so the picker is fed by this
 * module's own read-only `/api/lists` client.
 */
@Composable
private fun ListSourcePickerDialog(
    lists: List<ListSource>,
    isLoading: Boolean,
    onSelect: (ListSource) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a list") },
        text = {
            when {
                isLoading -> Box(
                    Modifier.fillMaxWidth().height(96.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                lists.isEmpty() -> Text("You don't have any lists yet.")

                else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                    items(lists, key = { it.id }) { list ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(list) }
                                .testTag(PoweredDocumentTestTags.listRow(list.id))
                                .padding(vertical = 12.dp),
                        ) {
                            Text(list.title, style = MaterialTheme.typography.titleSmall)
                            list.description?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Preview(showBackground = true)
@Composable
private fun PoweredDocumentPreview() {
    InterlinedListTheme {
        PoweredDocumentScreen(
            state = PoweredDocumentUiState(
                availability = com.interlinedlist.android.feature.ai.domain.AiAvailability.Available(
                    com.interlinedlist.android.feature.ai.domain.AiQuota(8, 50, 42),
                ),
                mode = PoweredDocumentMode.RESEARCH_URL,
                researchUrl = "https://example.com/widgets",
            ),
            onSelectMode = {},
            onInstructionChange = {},
            onResearchUrlChange = {},
            onOpenListPicker = {},
            onSelectList = {},
            onOpenDocumentPicker = {},
            onDocumentQueryChange = {},
            onSelectDocument = {},
            onClosePicker = {},
            onSuggest = {},
            onEditedTitleChange = {},
            onConfirm = {},
            onDiscard = {},
            onBack = {},
        )
    }
}
