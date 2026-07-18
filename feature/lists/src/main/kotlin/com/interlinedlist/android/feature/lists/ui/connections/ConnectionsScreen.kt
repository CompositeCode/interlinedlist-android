package com.interlinedlist.android.feature.lists.ui.connections

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.lists.domain.ListConnection
import com.interlinedlist.android.feature.lists.domain.ListSummary

/** Stable test tags for the connections screen. */
object ConnectionsTestTags {
    const val LIST = "connectionsList"
    const val CREATE_FAB = "connectionsCreateFab"
    const val EMPTY = "connectionsEmpty"
    const val PROGRESS = "connectionsProgress"
    const val ERROR = "connectionsError"
    const val SAVE = "connectionsSave"
    fun connection(id: String) = "connection_$id"
    fun remove(id: String) = "connectionRemove_$id"
}

/**
 * Hilt-wired entry for cross-list connections. This is a list-level screen (not
 * per-list), so it takes no id nav arg; [onBack] pops navigation.
 */
@Composable
fun ConnectionsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConnectionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ConnectionsScreen(
        state = state,
        onBack = onBack,
        onCreateConnection = { from, to, label -> viewModel.createConnection(from, to, label) },
        onDeleteConnection = viewModel::deleteConnection,
        modifier = modifier,
    )
}

/** Stateless connections screen — list, create, and remove links between lists. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionsScreen(
    state: ConnectionsUiState,
    onBack: () -> Unit,
    onCreateConnection: (String, String, String?) -> Unit,
    onDeleteConnection: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var creating by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Connections") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.canCreate) {
                ExtendedFloatingActionButton(
                    onClick = { creating = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Connect") },
                    modifier = Modifier.testTag(ConnectionsTestTags.CREATE_FAB),
                )
            }
        },
    ) { padding ->
        when {
            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(Modifier.testTag(ConnectionsTestTags.PROGRESS)) }

            else -> Column(Modifier.padding(padding)) {
                if (state.errorMessage != null) {
                    Text(
                        text = state.errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag(ConnectionsTestTags.ERROR),
                    )
                }
                if (state.isEmpty) {
                    EmptyState()
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag(ConnectionsTestTags.LIST),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.connections, key = { it.id }) { connection ->
                            ConnectionCard(
                                connection = connection,
                                onRemove = { onDeleteConnection(connection.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        ModalBottomSheet(onDismissRequest = { creating = false }, sheetState = sheetState) {
            ConnectionEditor(
                lists = state.lists,
                isSaving = state.isSaving,
                onCreate = { from, to, label ->
                    onCreateConnection(from, to, label)
                    creating = false
                },
                onCancel = { creating = false },
            )
        }
    }
}

@Composable
private fun ConnectionCard(connection: ListConnection, onRemove: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ConnectionsTestTags.connection(connection.id)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "${connection.fromListTitle} → ${connection.toListTitle}",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (!connection.label.isNullOrBlank()) {
                    Text(
                        text = connection.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.testTag(ConnectionsTestTags.remove(connection.id)),
            ) { Icon(Icons.Default.Delete, contentDescription = "Remove connection") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectionEditor(
    lists: List<ListSummary>,
    isSaving: Boolean,
    onCreate: (String, String, String?) -> Unit,
    onCancel: () -> Unit,
) {
    var fromId by remember { mutableStateOf<String?>(null) }
    var toId by remember { mutableStateOf<String?>(null) }
    var label by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("New connection", style = MaterialTheme.typography.titleLarge)

        Text("From", style = MaterialTheme.typography.labelMedium)
        ListPicker(lists = lists, selectedId = fromId, onSelect = { fromId = it })

        Text("To", style = MaterialTheme.typography.labelMedium)
        ListPicker(lists = lists, selectedId = toId, onSelect = { toId = it }, disabledId = fromId)

        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            label = { Text("Label (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Button(
                onClick = {
                    val from = fromId
                    val to = toId
                    if (from != null && to != null) onCreate(from, to, label.ifBlank { null })
                },
                enabled = !isSaving && fromId != null && toId != null && fromId != toId,
                modifier = Modifier.testTag(ConnectionsTestTags.SAVE),
            ) { Text("Connect") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListPicker(
    lists: List<ListSummary>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    disabledId: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        lists.forEach { list ->
            FilterChip(
                selected = selectedId == list.id,
                onClick = { onSelect(list.id) },
                enabled = list.id != disabledId,
                label = { Text(list.title.ifBlank { "Untitled" }) },
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(ConnectionsTestTags.EMPTY),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No connections yet", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Connect two lists to relate their data.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ConnectionsScreenPreview() {
    InterlinedListTheme {
        ConnectionsScreen(
            state = ConnectionsUiState(
                connections = listOf(
                    ListConnection("c1", "l1", "l2", "depends on", "Backlog", "Roadmap"),
                ),
                isLoading = false,
            ),
            onBack = {},
            onCreateConnection = { _, _, _ -> },
            onDeleteConnection = {},
        )
    }
}
