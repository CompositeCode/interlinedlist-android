package com.interlinedlist.android.feature.lists.ui.detail

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.lists.domain.ListRow
import com.interlinedlist.android.feature.lists.domain.ListSchema
import com.interlinedlist.android.feature.lists.domain.ListSummary
import com.interlinedlist.android.feature.lists.domain.SchemaField

/** Stable test tags for the list detail screen. */
object ListDetailTestTags {
    const val TABLE = "listDetailTable"
    const val ADD_ROW_FAB = "listDetailAddRow"
    const val EMPTY = "listDetailEmpty"
    const val PROGRESS = "listDetailProgress"
    const val ERROR = "listDetailError"
    const val SUBSCRIPTION = "listDetailSubscription"
    const val DELETE_LIST = "listDetailDeleteList"
    const val REFRESH = "listDetailRefresh"
    const val OVERFLOW = "listDetailOverflow"
    const val EDIT_SCHEMA = "listDetailEditSchema"
    const val WATCHERS = "listDetailWatchers"
    const val SHARE = "listDetailShare"
    fun row(id: String) = "listDetailRow_$id"
}

/**
 * Hilt-wired entry for a single list. Reads its `listId` from the nav
 * SavedStateHandle (see [LIST_ID_ARG]); [onBack] and [onListDeleted] let the app
 * pop navigation. [onEditSchema] and [onOpenWatchers] push the drill-down routes
 * for the list's columns and watchers (both keyed by the same list id).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListDetailRoute(
    onBack: () -> Unit,
    onListDeleted: () -> Unit,
    onEditSchema: () -> Unit,
    onOpenWatchers: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenShare: () -> Unit = {},
    viewModel: ListDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var editing by remember { mutableStateOf<EditorTarget?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val snackbarHostState = remember { SnackbarHostState() }

    // Surface the refresh outcome as a transient snackbar, then clear it.
    LaunchedEffect(state.refreshMessage) {
        state.refreshMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearRefreshMessage()
        }
    }

    ListDetailScreen(
        state = state,
        onBack = onBack,
        onAddRow = { editing = EditorTarget.New },
        onEditRow = { editing = EditorTarget.Existing(it) },
        onDeleteRow = viewModel::deleteRow,
        onDeleteList = { viewModel.deleteList(onListDeleted) },
        onRefresh = viewModel::refreshFromGithub,
        onEditSchema = onEditSchema,
        onOpenWatchers = onOpenWatchers,
        onOpenShare = onOpenShare,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )

    val target = editing
    if (target != null) {
        ModalBottomSheet(onDismissRequest = { editing = null }, sheetState = sheetState) {
            RowEditor(
                schema = state.schema,
                row = (target as? EditorTarget.Existing)?.row,
                isSaving = state.isSaving,
                onSave = { values ->
                    when (target) {
                        EditorTarget.New -> viewModel.addRow(values) { editing = null }
                        is EditorTarget.Existing -> viewModel.updateRow(target.row.id, values) { editing = null }
                    }
                },
                onCancel = { editing = null },
            )
        }
    }
}

private sealed interface EditorTarget {
    data object New : EditorTarget
    data class Existing(val row: ListRow) : EditorTarget
}

/** Stateless list detail — schema-driven table with loading / empty / error states. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListDetailScreen(
    state: ListDetailUiState,
    onBack: () -> Unit,
    onAddRow: () -> Unit,
    onEditRow: (ListRow) -> Unit,
    onDeleteRow: (String) -> Unit,
    onDeleteList: () -> Unit,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit = {},
    onEditSchema: () -> Unit = {},
    onOpenWatchers: () -> Unit = {},
    onOpenShare: () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var menuOpen by remember { mutableStateOf(false) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(state.title.ifBlank { "List" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.isRefreshing) {
                        CircularProgressIndicator(
                            Modifier
                                .padding(horizontal = 12.dp)
                                .height(20.dp)
                                .width(20.dp),
                        )
                    } else {
                        IconButton(onClick = onRefresh, modifier = Modifier.testTag(ListDetailTestTags.REFRESH)) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh from source")
                        }
                    }
                    IconButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier.testTag(ListDetailTestTags.OVERFLOW),
                    ) { Icon(Icons.Default.MoreVert, contentDescription = "More actions") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Edit columns") },
                            onClick = { menuOpen = false; onEditSchema() },
                            modifier = Modifier.testTag(ListDetailTestTags.EDIT_SCHEMA),
                        )
                        DropdownMenuItem(
                            text = { Text("Watchers") },
                            onClick = { menuOpen = false; onOpenWatchers() },
                            modifier = Modifier.testTag(ListDetailTestTags.WATCHERS),
                        )
                        DropdownMenuItem(
                            text = { Text("Share") },
                            onClick = { menuOpen = false; onOpenShare() },
                            modifier = Modifier.testTag(ListDetailTestTags.SHARE),
                        )
                        DropdownMenuItem(
                            text = { Text("Delete list") },
                            onClick = { menuOpen = false; onDeleteList() },
                            modifier = Modifier.testTag(ListDetailTestTags.DELETE_LIST),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (!state.subscriptionRequired && !state.schema.isEmpty) {
                FloatingActionButton(
                    onClick = onAddRow,
                    modifier = Modifier.testTag(ListDetailTestTags.ADD_ROW_FAB),
                ) { Icon(Icons.Default.Add, contentDescription = "Add row") }
            }
        },
    ) { padding ->
        when {
            state.subscriptionRequired -> Centered(Modifier.padding(padding).testTag(ListDetailTestTags.SUBSCRIPTION)) {
                Text("Subscribers only", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(state.errorMessage ?: "Lists require an active subscription.")
            }

            state.isLoading -> Centered(Modifier.padding(padding)) {
                CircularProgressIndicator(Modifier.testTag(ListDetailTestTags.PROGRESS))
            }

            state.errorMessage != null && state.summary == null -> Centered(
                Modifier.padding(padding).testTag(ListDetailTestTags.ERROR),
            ) { Text(state.errorMessage) }

            else -> Column(Modifier.padding(padding)) {
                if (!state.summary?.description.isNullOrBlank()) {
                    Text(
                        text = state.summary!!.description!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                SchemaTable(
                    schema = state.schema,
                    rows = state.rows,
                    isEmpty = state.isEmpty,
                    onEditRow = onEditRow,
                    onDeleteRow = onDeleteRow,
                )
            }
        }
    }
}

/**
 * Renders rows generically against the schema: a header of field labels and one
 * card per row projecting each field's value. Nothing about the columns is
 * hardcoded — an empty schema still shows raw key/value pairs from the rows.
 */
@Composable
private fun SchemaTable(
    schema: ListSchema,
    rows: List<ListRow>,
    isEmpty: Boolean,
    onEditRow: (ListRow) -> Unit,
    onDeleteRow: (String) -> Unit,
) {
    if (isEmpty) {
        Centered(Modifier.testTag(ListDetailTestTags.EMPTY)) {
            Text("No rows yet", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Use the + button to add the first row.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    // Columns come from the schema; fall back to the union of row keys if absent.
    val columns: List<SchemaField> = schema.fields.ifEmpty {
        rows.flatMap { it.values.keys }.distinct().map { key ->
            SchemaField(key = key, label = key, type = com.interlinedlist.android.feature.lists.domain.FieldType.TEXT)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag(ListDetailTestTags.TABLE),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(rows, key = { it.id }) { row ->
            RowCard(
                columns = columns,
                row = row,
                onClick = { onEditRow(row) },
                onDelete = { onDeleteRow(row.id) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RowCard(
    columns: List<SchemaField>,
    row: ListRow,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ListDetailTestTags.row(row.id)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                    columns.forEach { field ->
                        RowField(label = field.label, value = row.valueFor(field.key))
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete row")
                }
            }
        }
    }
}

@Composable
private fun RowField(label: String, value: String) {
    Row(Modifier.padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(120.dp),
        )
        Text(
            text = value.ifBlank { "—" },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun Centered(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, content = { content() })
    }
}

@Preview(showBackground = true)
@Composable
private fun ListDetailScreenPreview() {
    InterlinedListTheme {
        ListDetailScreen(
            state = ListDetailUiState(
                summary = ListSummary("1", "Reading list", "Books", 2, null, false, null),
                schema = ListSchema(
                    listOf(
                        SchemaField("title", "Title", com.interlinedlist.android.feature.lists.domain.FieldType.TEXT),
                        SchemaField("done", "Done", com.interlinedlist.android.feature.lists.domain.FieldType.BOOLEAN),
                    ),
                ),
                rows = listOf(
                    ListRow("r1", mapOf("title" to "Dune", "done" to "true")),
                    ListRow("r2", mapOf("title" to "Hyperion", "done" to "false")),
                ),
                isLoading = false,
            ),
            onBack = {},
            onAddRow = {},
            onEditRow = {},
            onDeleteRow = {},
            onDeleteList = {},
        )
    }
}
