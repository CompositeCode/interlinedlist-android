package com.interlinedlist.android.feature.lists.ui.schema

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.lists.domain.FieldType
import com.interlinedlist.android.feature.lists.domain.ListSummary

/** Stable test tags for the schema editor. */
object SchemaEditorTestTags {
    const val LIST = "schemaEditorList"
    const val ADD_COLUMN = "schemaEditorAddColumn"
    const val SAVE = "schemaEditorSave"
    const val PROGRESS = "schemaEditorProgress"
    const val ERROR = "schemaEditorError"
    const val SUBSCRIPTION = "schemaEditorSubscription"
    const val LOCKED_NOTICE = "schemaEditorLockedNotice"
    const val PARENT_PICKER = "schemaEditorParentPicker"
    fun column(uiId: Long) = "schemaColumn_$uiId"
    fun key(uiId: Long) = "schemaColumnKey_$uiId"
    fun remove(uiId: Long) = "schemaColumnRemove_$uiId"
    fun parentOption(id: String) = "schemaEditorParent_$id"
}

/**
 * Hilt-wired entry for editing a list's schema (columns). Reads its `listId` from
 * the nav SavedStateHandle (see [SCHEMA_LIST_ID_ARG]). [onBack] pops navigation;
 * [onSaved] is invoked after a successful save so the app can pop back to detail.
 */
@Composable
fun SchemaEditorRoute(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SchemaEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Pop back once the schema has been persisted.
    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }

    SchemaEditorScreen(
        state = state,
        onBack = onBack,
        onAddColumn = viewModel::addColumn,
        onRemoveColumn = viewModel::removeColumn,
        onKeyChange = viewModel::updateKey,
        onLabelChange = viewModel::updateLabel,
        onTypeChange = viewModel::updateType,
        onSave = { viewModel.save() },
        onSelectParent = viewModel::setParent,
        modifier = modifier,
    )
}

/** Stateless schema editor — add/edit/remove typed columns with loading/error states. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchemaEditorScreen(
    state: SchemaEditorUiState,
    onBack: () -> Unit,
    onAddColumn: () -> Unit,
    onRemoveColumn: (Long) -> Unit,
    onKeyChange: (Long, String) -> Unit,
    onLabelChange: (Long, String) -> Unit,
    onTypeChange: (Long, FieldType) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    onSelectParent: (String) -> Unit = {},
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(if (state.isSchemaLocked) "Columns & parent" else "Edit columns") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Nothing to save on a locked schema: the parent picker
                    // persists on selection.
                    if (state.canEditColumns) {
                        TextButton(
                            onClick = onSave,
                            enabled = state.canSave,
                            modifier = Modifier.testTag(SchemaEditorTestTags.SAVE),
                        ) { Text("Save") }
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.canEditColumns && !state.subscriptionRequired && !state.isLoading) {
                ExtendedFloatingActionButton(
                    onClick = onAddColumn,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add column") },
                    modifier = Modifier.testTag(SchemaEditorTestTags.ADD_COLUMN),
                )
            }
        },
    ) { padding ->
        when {
            state.subscriptionRequired -> Centered(Modifier.padding(padding).testTag(SchemaEditorTestTags.SUBSCRIPTION)) {
                Text("Subscribers only", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(state.errorMessage ?: "Editing lists requires an active subscription.")
            }

            state.isLoading -> Centered(Modifier.padding(padding)) {
                CircularProgressIndicator(Modifier.testTag(SchemaEditorTestTags.PROGRESS))
            }

            else -> Column(Modifier.padding(padding)) {
                if (state.errorMessage != null) {
                    Text(
                        text = state.errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag(SchemaEditorTestTags.ERROR),
                    )
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(SchemaEditorTestTags.LIST),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.isSchemaLocked) {
                        item { LockedNotice(state.githubRepo) }
                        item {
                            ParentPicker(
                                parentId = state.parentId,
                                options = state.parentOptions,
                                enabled = !state.isSaving,
                                onSelectParent = onSelectParent,
                            )
                        }
                    }
                    items(state.columns, key = { it.uiId }) { column ->
                        if (state.canEditColumns) {
                            ColumnCard(
                                column = column,
                                onKeyChange = { onKeyChange(column.uiId, it) },
                                onLabelChange = { onLabelChange(column.uiId, it) },
                                onTypeChange = { onTypeChange(column.uiId, it) },
                                onRemove = { onRemoveColumn(column.uiId) },
                            )
                        } else {
                            LockedColumnCard(column)
                        }
                    }
                    if (state.columns.isEmpty() && state.canEditColumns) {
                        item {
                            Text(
                                "No columns yet. Use Add column to define this list's shape.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Why the columns cannot be edited. A GitHub-backed list's schema is the fixed
 * set of issue fields, so saying so plainly beats letting someone edit a form
 * whose save the server will refuse.
 */
@Composable
private fun LockedNotice(githubRepo: String?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(SchemaEditorTestTags.LOCKED_NOTICE),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Columns are set by GitHub", style = MaterialTheme.typography.titleSmall)
            Text(
                text = buildString {
                    append("This list mirrors issues")
                    if (!githubRepo.isNullOrBlank()) append(" in $githubRepo")
                    append(
                        ", so its columns are fixed: title, body, labels, assignees and " +
                            "state come from GitHub and cannot be changed here. The parent " +
                            "list is the one thing you can still change.",
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A fixed GitHub column, rendered for reference only. */
@Composable
private fun LockedColumnCard(column: EditableColumn) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(SchemaEditorTestTags.column(column.uiId)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(column.label.ifBlank { column.key }, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = column.key + " · " + column.type.name.lowercase(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (column.readOnly) {
                Text(
                    text = "Set by GitHub",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The only edit a locked schema allows: where this list hangs in the tree. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParentPicker(
    parentId: String?,
    options: List<ListSummary>,
    enabled: Boolean,
    onSelectParent: (String) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(SchemaEditorTestTags.PARENT_PICKER),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Parent list", style = MaterialTheme.typography.titleSmall)
            if (options.isEmpty()) {
                Text(
                    "No other lists to nest this one under yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            options.forEach { option ->
                FilterChip(
                    selected = option.id == parentId,
                    enabled = enabled,
                    onClick = { onSelectParent(option.id) },
                    label = { Text(option.title.ifBlank { "Untitled list" }) },
                    modifier = Modifier.testTag(SchemaEditorTestTags.parentOption(option.id)),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnCard(
    column: EditableColumn,
    onKeyChange: (String) -> Unit,
    onLabelChange: (String) -> Unit,
    onTypeChange: (FieldType) -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(SchemaEditorTestTags.column(column.uiId)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = column.key,
                    onValueChange = onKeyChange,
                    label = { Text("Field key") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(SchemaEditorTestTags.key(column.uiId)),
                )
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.testTag(SchemaEditorTestTags.remove(column.uiId)),
                ) { Icon(Icons.Default.Delete, contentDescription = "Remove column") }
            }
            OutlinedTextField(
                value = column.label,
                onValueChange = onLabelChange,
                label = { Text("Label") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Type", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldType.entries.forEach { type ->
                    FilterChip(
                        selected = column.type == type,
                        onClick = { onTypeChange(type) },
                        label = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Centered(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, content = { content() })
    }
}

@Preview(showBackground = true)
@Composable
private fun SchemaEditorScreenPreview() {
    InterlinedListTheme {
        SchemaEditorScreen(
            state = SchemaEditorUiState(
                columns = listOf(
                    EditableColumn(0, "title", "Title", FieldType.TEXT),
                    EditableColumn(1, "pages", "Pages", FieldType.NUMBER),
                ),
                isLoading = false,
            ),
            onBack = {},
            onAddColumn = {},
            onRemoveColumn = {},
            onKeyChange = { _, _ -> },
            onLabelChange = { _, _ -> },
            onTypeChange = { _, _ -> },
            onSave = {},
        )
    }
}
