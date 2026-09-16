package com.interlinedlist.android.core.materialize.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.core.materialize.domain.DocumentListStyle
import com.interlinedlist.android.core.materialize.domain.ListColumnType
import com.interlinedlist.android.core.materialize.domain.MaterializeColumn
import com.interlinedlist.android.core.materialize.domain.MaterializeSource
import com.interlinedlist.android.core.materialize.domain.MaterializeTarget
import com.interlinedlist.android.core.materialize.domain.MaterializedDocument
import com.interlinedlist.android.core.materialize.domain.MaterializedList
import com.interlinedlist.android.core.materialize.domain.MessageDraft
import com.interlinedlist.android.core.materialize.domain.RowDataStyle

/** Stable test tags for the "Create from…" window. */
object MaterializeWindowTestTags {
    const val WINDOW = "materializeWindow"
    const val TITLE = "materializeTitle"
    const val DESCRIPTION = "materializeDescription"
    const val VISIBILITY = "materializeVisibility"
    const val ADD_COLUMN = "materializeAddColumn"
    const val TABLE_PREVIEW = "materializeTablePreview"
    const val DOCUMENT_PREVIEW = "materializeDocumentPreview"
    const val DRAFT_PREVIEW = "materializeDraftPreview"
    const val FILE_NAME = "materializeFileName"
    const val CONFIRM = "materializeConfirm"
    const val CANCEL = "materializeCancel"
    const val PROGRESS = "materializeProgress"
    const val ERROR = "materializeError"
    const val SUBSCRIPTION = "materializeSubscription"
    const val SUCCESS = "materializeSuccess"
    const val OPEN_LIST = "materializeOpenList"
    const val OPEN_DOCUMENT = "materializeOpenDocument"
    const val OPEN_COMPOSER = "materializeOpenComposer"
    fun destination(target: MaterializeTarget) = "materializeDestination_${target.apiValue}"
    fun column(uiId: Long) = "materializeColumn_$uiId"
    fun columnName(uiId: Long) = "materializeColumnName_$uiId"
    fun columnType(uiId: Long) = "materializeColumnType_$uiId"
    fun removeColumn(uiId: Long) = "materializeColumnRemove_$uiId"
    fun listStyle(style: DocumentListStyle) = "materializeListStyle_${style.apiValue}"
    fun rowDataStyle(style: RowDataStyle) = "materializeRowDataStyle_${style.apiValue}"
}

/**
 * The "Create from…" finalization window: shows exactly what will be created,
 * lets the user change it, and writes nothing until they confirm.
 *
 * Entry-point agnostic by construction — it takes a [MaterializeLaunch] (an
 * id-only source, the destination the menu picked, and a preview of the source)
 * and reports back through [onOpenList] / [onOpenDocument] / [onUseDraft], so
 * messages, lists, rows, documents and document selections all share one window
 * instead of each re-implementing the preview and the confirm discipline.
 */
@Composable
fun MaterializeWindow(
    launch: MaterializeLaunch,
    onDismiss: () -> Unit,
    onOpenList: (MaterializedList) -> Unit,
    onOpenDocument: (MaterializedDocument) -> Unit,
    onUseDraft: (MessageDraft) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: MaterializeWindowViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(launch) { viewModel.start(launch) }

    state?.let { current ->
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = modifier
                    .fillMaxWidth(WINDOW_WIDTH_FRACTION)
                    .fillMaxHeight(WINDOW_HEIGHT_FRACTION)
                    .testTag(MaterializeWindowTestTags.WINDOW),
                shape = MaterialTheme.shapes.large,
                tonalElevation = 6.dp,
            ) {
                MaterializeWindowContent(
                    state = current,
                    onSelectTarget = viewModel::selectTarget,
                    onTitleChange = viewModel::updateTitle,
                    onDescriptionChange = viewModel::updateDescription,
                    onPublicChange = viewModel::setPublic,
                    onAddColumn = viewModel::addColumn,
                    onRemoveColumn = viewModel::removeColumn,
                    onColumnNameChange = viewModel::renameColumn,
                    onColumnTypeChange = viewModel::changeColumnType,
                    onFileNameChange = viewModel::updateFileName,
                    onListStyleChange = viewModel::selectListStyle,
                    onRowDataStyleChange = viewModel::selectRowDataStyle,
                    onConfirm = viewModel::confirm,
                    onDismiss = onDismiss,
                    onOpenList = onOpenList,
                    onOpenDocument = onOpenDocument,
                    onUseDraft = onUseDraft,
                )
            }
        }
    }
}

/** The window's stateless body: destination switcher, editor, preview, confirm. */
@Composable
fun MaterializeWindowContent(
    state: MaterializeWindowUiState,
    onSelectTarget: (MaterializeTarget) -> Unit,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onPublicChange: (Boolean) -> Unit,
    onAddColumn: () -> Unit,
    onRemoveColumn: (Long) -> Unit,
    onColumnNameChange: (Long, String) -> Unit,
    onColumnTypeChange: (Long, ListColumnType) -> Unit,
    onFileNameChange: (String) -> Unit,
    onListStyleChange: (DocumentListStyle) -> Unit,
    onRowDataStyleChange: (RowDataStyle) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onOpenList: (MaterializedList) -> Unit,
    onOpenDocument: (MaterializedDocument) -> Unit,
    onUseDraft: (MessageDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        WindowHeader(onDismiss = onDismiss)

        if (state.success == null) {
            DestinationSwitcher(
                targets = state.availableTargets,
                selected = state.target,
                onSelect = onSelectTarget,
            )
        }
        HorizontalDivider()

        Box(Modifier.weight(1f)) {
            if (state.success != null) {
                SuccessPane(
                    success = state.success,
                    onOpenList = onOpenList,
                    onOpenDocument = onOpenDocument,
                    onUseDraft = onUseDraft,
                )
            } else {
                EditorPane(
                    state = state,
                    onTitleChange = onTitleChange,
                    onDescriptionChange = onDescriptionChange,
                    onPublicChange = onPublicChange,
                    onAddColumn = onAddColumn,
                    onRemoveColumn = onRemoveColumn,
                    onColumnNameChange = onColumnNameChange,
                    onColumnTypeChange = onColumnTypeChange,
                    onFileNameChange = onFileNameChange,
                    onListStyleChange = onListStyleChange,
                    onRowDataStyleChange = onRowDataStyleChange,
                )
            }
        }

        HorizontalDivider()
        WindowFooter(state = state, onConfirm = onConfirm, onDismiss = onDismiss)
    }
}

@Composable
private fun WindowHeader(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Create from…",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Close")
        }
    }
}

/** The destination can be changed here, without losing what has been edited. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DestinationSwitcher(
    targets: List<MaterializeTarget>,
    selected: MaterializeTarget,
    onSelect: (MaterializeTarget) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        targets.forEach { target ->
            FilterChip(
                selected = target == selected,
                onClick = { onSelect(target) },
                label = { Text(target.label) },
                modifier = Modifier.testTag(MaterializeWindowTestTags.destination(target)),
            )
        }
    }
}

@Composable
private fun EditorPane(
    state: MaterializeWindowUiState,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onPublicChange: (Boolean) -> Unit,
    onAddColumn: () -> Unit,
    onRemoveColumn: (Long) -> Unit,
    onColumnNameChange: (Long, String) -> Unit,
    onColumnTypeChange: (Long, ListColumnType) -> Unit,
    onFileNameChange: (String) -> Unit,
    onListStyleChange: (DocumentListStyle) -> Unit,
    onRowDataStyleChange: (RowDataStyle) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Banners(state)

        if (state.createsDraft) {
            DraftNotice(state.preview.draftBody)
            return@Column
        }

        OutlinedTextField(
            value = state.title,
            onValueChange = onTitleChange,
            label = { Text(if (state.createsList) "List title" else "Document title") },
            isError = state.titleError != null,
            supportingText = state.titleError?.let { { Text(it) } },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(MaterializeWindowTestTags.TITLE),
        )

        VisibilityRow(state = state, onPublicChange = onPublicChange)

        if (state.createsList) {
            OutlinedTextField(
                value = state.description,
                onValueChange = onDescriptionChange,
                label = { Text("Description (optional)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(MaterializeWindowTestTags.DESCRIPTION),
            )
            ColumnsEditor(
                columns = state.columns,
                onAddColumn = onAddColumn,
                onRemoveColumn = onRemoveColumn,
                onColumnNameChange = onColumnNameChange,
                onColumnTypeChange = onColumnTypeChange,
            )
            TablePreview(state)
        }

        if (state.createsDocument) {
            if (state.createsList) HorizontalDivider()
            OutlinedTextField(
                value = state.fileName,
                onValueChange = onFileNameChange,
                label = { Text("File name (optional)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(MaterializeWindowTestTags.FILE_NAME),
            )
            if (state.showsRowLayoutOptions) {
                DocumentStyleOptions(
                    state = state,
                    onListStyleChange = onListStyleChange,
                    onRowDataStyleChange = onRowDataStyleChange,
                )
            }
            DocumentPreviewPane(state)
        }
    }
}

@Composable
private fun Banners(state: MaterializeWindowUiState) {
    if (state.subscriptionRequired) {
        Card(modifier = Modifier.testTag(MaterializeWindowTestTags.SUBSCRIPTION)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Subscribers only", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = state.errorMessage
                        ?: "Creating lists and documents requires an active subscription.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    } else if (state.errorMessage != null) {
        Text(
            text = state.errorMessage,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(MaterializeWindowTestTags.ERROR),
        )
    }
}

/** The one destination that creates nothing: it hands the composer a draft. */
@Composable
private fun DraftNotice(draftBody: String?) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Opens in the composer", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Nothing is posted here. InterlinedList builds the draft from your " +
                    "selection and the composer opens with it, so you can edit it, add " +
                    "cross-post targets or a schedule, and post it yourself.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    // What the source reads as, when the entry point could work it out. The
    // server still builds and sizes the body it hands the composer, so this is
    // shown as a preview and never sent.
    draftBody?.takeIf { it.isNotBlank() }?.let { body ->
        Card(Modifier.testTag(MaterializeWindowTestTags.DRAFT_PREVIEW)) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

@Composable
private fun VisibilityRow(state: MaterializeWindowUiState, onPublicChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MaterializeWindowTestTags.VISIBILITY),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Make it public", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = if (state.isPublic) {
                    "Anyone with the link can see it."
                } else {
                    "Only you can see it."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = state.isPublic, onCheckedChange = onPublicChange)
    }
}

@Composable
private fun ColumnsEditor(
    columns: List<EditableColumn>,
    onAddColumn: () -> Unit,
    onRemoveColumn: (Long) -> Unit,
    onColumnNameChange: (Long, String) -> Unit,
    onColumnTypeChange: (Long, ListColumnType) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Columns", style = MaterialTheme.typography.titleMedium)
        columns.forEach { column ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(MaterializeWindowTestTags.column(column.uiId)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = column.name,
                    onValueChange = { onColumnNameChange(column.uiId, it) },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(MaterializeWindowTestTags.columnName(column.uiId)),
                )
                ColumnTypePicker(
                    column = column,
                    onColumnTypeChange = { onColumnTypeChange(column.uiId, it) },
                )
                IconButton(
                    onClick = { onRemoveColumn(column.uiId) },
                    modifier = Modifier.testTag(MaterializeWindowTestTags.removeColumn(column.uiId)),
                ) { Icon(Icons.Default.Delete, contentDescription = "Remove column") }
            }
        }
        TextButton(
            onClick = onAddColumn,
            modifier = Modifier.testTag(MaterializeWindowTestTags.ADD_COLUMN),
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Add column")
        }
    }
}

@Composable
private fun ColumnTypePicker(
    column: EditableColumn,
    onColumnTypeChange: (ListColumnType) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.testTag(MaterializeWindowTestTags.columnType(column.uiId)),
        ) {
            Text(column.type.label)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            // The twelve types the schema accepts; anything else is a bad_request.
            ListColumnType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.label) },
                    onClick = {
                        expanded = false
                        onColumnTypeChange(type)
                    },
                )
            }
        }
    }
}

/** The live table: the rows and columns the list will be created with. */
@Composable
private fun TablePreview(state: MaterializeWindowUiState) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.testTag(MaterializeWindowTestTags.TABLE_PREVIEW),
    ) {
        Text("Preview", style = MaterialTheme.typography.titleMedium)
        when {
            state.columns.isEmpty() -> PreviewHint("This list will start empty. Add a column to shape it.")

            else -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                Row {
                    state.columns.forEach { column ->
                        PreviewCell(
                            text = column.name.ifBlank { "Untitled" },
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                HorizontalDivider()
                state.preview.rows.forEach { row ->
                    Row {
                        state.columns.forEach { column ->
                            PreviewCell(
                                // A user-added column has no source data: it is created empty.
                                text = column.propertyKey?.let { row.values[it] }.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
        PreviewHint(state.preview.rowSummary)
    }
}

/** The document as it would read, in the chosen styles. */
@Composable
private fun DocumentPreviewPane(state: MaterializeWindowUiState) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.testTag(MaterializeWindowTestTags.DOCUMENT_PREVIEW),
    ) {
        Text("Preview", style = MaterialTheme.typography.titleMedium)
        val rendered = state.documentPreview
        Card(Modifier.fillMaxWidth()) {
            Text(
                text = rendered.ifBlank { "InterlinedList will build this document from your selection." },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentStyleOptions(
    state: MaterializeWindowUiState,
    onListStyleChange: (DocumentListStyle) -> Unit,
    onRowDataStyleChange: (RowDataStyle) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("List style", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DocumentListStyle.entries.forEach { style ->
                FilterChip(
                    selected = state.listStyle == style,
                    onClick = { onListStyleChange(style) },
                    label = { Text(style.label) },
                    modifier = Modifier.testTag(MaterializeWindowTestTags.listStyle(style)),
                )
            }
        }
        Text("Row data", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RowDataStyle.entries.forEach { style ->
                FilterChip(
                    selected = state.rowDataStyle == style,
                    onClick = { onRowDataStyleChange(style) },
                    label = { Text(style.label) },
                    modifier = Modifier.testTag(MaterializeWindowTestTags.rowDataStyle(style)),
                )
            }
        }
    }
}

/**
 * What was created, with a link into each of it. A `both` conversion made two
 * objects, so it offers both links.
 */
@Composable
private fun SuccessPane(
    success: MaterializeSuccess,
    onOpenList: (MaterializedList) -> Unit,
    onOpenDocument: (MaterializedDocument) -> Unit,
    onUseDraft: (MessageDraft) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag(MaterializeWindowTestTags.SUCCESS),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val draft = success.draft
        if (draft != null) {
            Text("Your draft is ready", style = MaterialTheme.typography.titleMedium)
            Text(
                text = draft.content,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
            )
            Button(
                onClick = { onUseDraft(draft) },
                modifier = Modifier.testTag(MaterializeWindowTestTags.OPEN_COMPOSER),
            ) { Text("Open in composer") }
            return@Column
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("Created", style = MaterialTheme.typography.titleMedium)
        }

        success.list?.let { list ->
            Button(
                onClick = { onOpenList(list) },
                modifier = Modifier.testTag(MaterializeWindowTestTags.OPEN_LIST),
            ) { Text("Open list") }
            Text(
                text = list.title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        success.document?.let { document ->
            Button(
                onClick = { onOpenDocument(document) },
                modifier = Modifier.testTag(MaterializeWindowTestTags.OPEN_DOCUMENT),
            ) { Text("Open document") }
            Text(
                text = document.title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WindowFooter(
    state: MaterializeWindowUiState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.testTag(MaterializeWindowTestTags.CANCEL),
        ) { Text(if (state.success == null) "Cancel" else "Done") }

        if (state.success == null) {
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onConfirm,
                enabled = state.canConfirm,
                modifier = Modifier.testTag(MaterializeWindowTestTags.CONFIRM),
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(16.dp)
                            .testTag(MaterializeWindowTestTags.PROGRESS),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (state.createsDraft) "Build draft" else "Create")
            }
        }
    }
}

@Composable
private fun PreviewCell(text: String, style: TextStyle) {
    Text(
        text = text,
        style = style,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .width(CELL_WIDTH)
            .padding(8.dp),
    )
}

@Composable
private fun PreviewHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** How many rows the new list gets, and how much of it is on screen. */
private val MaterializePreview.rowSummary: String
    get() = when {
        totalRowCount == 0 -> "No rows to preview."
        hiddenRowCount > 0 -> "Showing ${rows.size} of $totalRowCount rows."
        totalRowCount == 1 -> "1 row."
        else -> "$totalRowCount rows."
    }

private val MaterializeTarget.label: String
    get() = when (this) {
        MaterializeTarget.LIST -> "To List"
        MaterializeTarget.DOC -> "To Doc"
        MaterializeTarget.BOTH -> "To List & Doc"
        MaterializeTarget.MESSAGE -> "To Message"
    }

private val DocumentListStyle.label: String
    get() = when (this) {
        DocumentListStyle.NUMBERED -> "Numbered"
        DocumentListStyle.BULLETED -> "Bulleted"
    }

private val RowDataStyle.label: String
    get() = when (this) {
        RowDataStyle.INLINE -> "Inline"
        RowDataStyle.SUB_ITEMS -> "Sub-items"
    }

/** Readable names for the twelve column types the list schema accepts. */
internal val ListColumnType.label: String
    get() = when (this) {
        ListColumnType.TEXT -> "Text"
        ListColumnType.TEXTAREA -> "Long text"
        ListColumnType.NUMBER -> "Number"
        ListColumnType.BOOLEAN -> "Yes / no"
        ListColumnType.DATE -> "Date"
        ListColumnType.DATETIME -> "Date & time"
        ListColumnType.EMAIL -> "Email"
        ListColumnType.URL -> "URL"
        ListColumnType.TEL -> "Phone"
        ListColumnType.SELECT -> "Select"
        ListColumnType.MULTISELECT -> "Multi-select"
        ListColumnType.PRIORITY -> "Priority"
    }

private const val WINDOW_WIDTH_FRACTION = 0.96f
private const val WINDOW_HEIGHT_FRACTION = 0.92f
private val CELL_WIDTH = 140.dp

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun MaterializeWindowPreview() {
    InterlinedListTheme {
        MaterializeWindowContent(
            state = previewState(),
            onSelectTarget = {},
            onTitleChange = {},
            onDescriptionChange = {},
            onPublicChange = {},
            onAddColumn = {},
            onRemoveColumn = {},
            onColumnNameChange = { _, _ -> },
            onColumnTypeChange = { _, _ -> },
            onFileNameChange = {},
            onListStyleChange = {},
            onRowDataStyleChange = {},
            onConfirm = {},
            onDismiss = {},
            onOpenList = {},
            onOpenDocument = {},
            onUseDraft = {},
        )
    }
}

private fun previewState() = MaterializeWindowUiState.from(
    MaterializeLaunch(
        source = MaterializeSource.Lists(listOf("lst_1")),
        initialTarget = MaterializeTarget.BOTH,
        preview = MaterializePreview(
            suggestedTitle = "Books to Read",
            suggestedDescription = "My reading backlog.",
            suggestedFileName = "books-to-read.md",
            columns = listOf(
                MaterializeColumn("title", "Title", ListColumnType.TEXT, sourceKey = "title"),
                MaterializeColumn("author", "Author", ListColumnType.TEXT, sourceKey = "author"),
            ),
            rows = listOf(
                MaterializePreviewRow(mapOf("title" to "The Dream Machine", "author" to "Waldrop")),
                MaterializePreviewRow(mapOf("title" to "Thinking in Systems", "author" to "Meadows")),
            ),
            totalRowCount = 340,
        ),
    ),
)
