package com.interlinedlist.android.feature.lists.ui.views

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.interlinedlist.android.feature.lists.domain.ListView
import com.interlinedlist.android.feature.lists.domain.ListViewConfig
import com.interlinedlist.android.feature.lists.domain.ListViewScope

/** Stable test tags for the saved-view switcher. */
object ListViewSwitcherTestTags {
    const val BAR = "listViewSwitcherBar"
    const val SHEET = "listViewSwitcherSheet"
    const val EMPTY = "listViewSwitcherEmpty"
    const val ERROR = "listViewSwitcherError"
    const val CREATE_NAME = "listViewCreateName"
    const val CREATE_SUBMIT = "listViewCreateSubmit"
    const val RENAME_DIALOG = "listViewRenameDialog"
    const val RENAME_FIELD = "listViewRenameField"
    const val RENAME_CONFIRM = "listViewRenameConfirm"
    fun scope(scope: ListViewScope) = "listViewScope_${scope.apiValue}"
    fun view(id: String) = "listView_$id"
    fun overflow(id: String) = "listViewOverflow_$id"
    fun setDefault(id: String) = "listViewSetDefault_$id"
    fun fork(id: String) = "listViewFork_$id"
    fun rename(id: String) = "listViewRename_$id"
    fun delete(id: String) = "listViewDelete_$id"
}

/**
 * Hilt-wired saved-view switcher for the list detail screen: a bar showing the
 * view in use, and a sheet to switch between the list's shared views and the
 * user's personal ones, create, rename, delete, re-default or fork them.
 *
 * It reads the same `listId` nav argument as the detail screen, so it drops into
 * that route without any extra wiring.
 */
@Composable
fun ListViewSwitcher(
    modifier: Modifier = Modifier,
    viewModel: ListViewsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf<ListView?>(null) }

    ListViewSwitcherBar(
        state = state,
        onOpen = viewModel::openSwitcher,
        modifier = modifier,
    )

    if (state.isSwitcherOpen) {
        ListViewSwitcherSheet(
            state = state,
            onDismiss = viewModel::closeSwitcher,
            onSelect = { viewModel.selectView(it.id) },
            onSetDefault = viewModel::setDefault,
            onFork = viewModel::forkView,
            onRename = { renaming = it },
            onDelete = viewModel::deleteView,
            onCreate = { name, scope -> viewModel.createView(name, scope) },
        )
    }

    renaming?.let { view ->
        RenameViewDialog(
            view = view,
            onDismiss = { renaming = null },
            onConfirm = { name ->
                viewModel.renameView(view, name)
                renaming = null
            },
        )
    }
}

/** The always-visible bar: which view is in use, and a way into the switcher. */
@Composable
fun ListViewSwitcherBar(
    state: ListViewsUiState,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = state.selectedView
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag(ListViewSwitcherTestTags.BAR),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = selected?.name?.ifBlank { UNTITLED_VIEW } ?: "All records",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = selected?.let(::viewSummary) ?: "No saved view",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected?.isDefault == true) {
            DefaultBadge()
        }
        Icon(Icons.Default.ArrowDropDown, contentDescription = "Switch view")
    }
}

/** The switcher itself, as a modal sheet over the list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListViewSwitcherSheet(
    state: ListViewsUiState,
    onDismiss: () -> Unit,
    onSelect: (ListView) -> Unit,
    onSetDefault: (ListView) -> Unit,
    onFork: (ListView) -> Unit,
    onRename: (ListView) -> Unit,
    onDelete: (ListView) -> Unit,
    onCreate: (String, ListViewScope) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier.testTag(ListViewSwitcherTestTags.SHEET),
    ) {
        ListViewSwitcherContent(
            state = state,
            onSelect = onSelect,
            onSetDefault = onSetDefault,
            onFork = onFork,
            onRename = onRename,
            onDelete = onDelete,
            onCreate = onCreate,
        )
    }
}

/** Stateless switcher body — shared views, personal views, and a create form. */
@Composable
fun ListViewSwitcherContent(
    state: ListViewsUiState,
    onSelect: (ListView) -> Unit,
    onSetDefault: (ListView) -> Unit,
    onFork: (ListView) -> Unit,
    onRename: (ListView) -> Unit,
    onDelete: (ListView) -> Unit,
    onCreate: (String, ListViewScope) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        Text("Views", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Shared views are on the list for everyone. Personal views are only yours.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        state.errorMessage?.let { message ->
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(ListViewSwitcherTestTags.ERROR),
            )
        }

        if (state.isLoading) {
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator(Modifier.height(24.dp).width(24.dp))
        }

        if (state.isEmpty) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "No saved views yet. Create one below to keep a way of looking at this list.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(ListViewSwitcherTestTags.EMPTY),
            )
        }

        ViewSection(
            title = "Shared",
            views = state.sharedViews,
            state = state,
            onSelect = onSelect,
            onSetDefault = onSetDefault,
            onFork = onFork,
            onRename = onRename,
            onDelete = onDelete,
        )
        ViewSection(
            title = "Personal",
            views = state.personalViews,
            state = state,
            onSelect = onSelect,
            onSetDefault = onSetDefault,
            onFork = onFork,
            onRename = onRename,
            onDelete = onDelete,
        )

        Spacer(Modifier.height(20.dp))
        CreateViewForm(isSaving = state.isSaving, onCreate = onCreate)
    }
}

@Composable
private fun ViewSection(
    title: String,
    views: List<ListView>,
    state: ListViewsUiState,
    onSelect: (ListView) -> Unit,
    onSetDefault: (ListView) -> Unit,
    onFork: (ListView) -> Unit,
    onRename: (ListView) -> Unit,
    onDelete: (ListView) -> Unit,
) {
    if (views.isEmpty()) return
    Spacer(Modifier.height(16.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    views.forEach { view ->
        ViewRow(
            view = view,
            isSelected = view.id == state.selectedViewId,
            canModify = state.canModify(view),
            canFork = state.canFork(view),
            onSelect = { onSelect(view) },
            onSetDefault = { onSetDefault(view) },
            onFork = { onFork(view) },
            onRename = { onRename(view) },
            onDelete = { onDelete(view) },
        )
    }
}

@Composable
private fun ViewRow(
    view: ListView,
    isSelected: Boolean,
    canModify: Boolean,
    canFork: Boolean,
    onSelect: () -> Unit,
    onSetDefault: () -> Unit,
    onFork: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    ListItem(
        modifier = Modifier
            .clickable(onClick = onSelect)
            .testTag(ListViewSwitcherTestTags.view(view.id)),
        headlineContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = view.name.ifBlank { UNTITLED_VIEW },
                    style = if (isSelected) {
                        MaterialTheme.typography.titleSmall
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (view.isDefault) DefaultBadge()
            }
        },
        supportingContent = {
            Text(
                text = viewSummary(view) + if (canModify) "" else " · read-only",
                style = MaterialTheme.typography.labelSmall,
            )
        },
        trailingContent = {
            Box {
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.testTag(ListViewSwitcherTestTags.overflow(view.id)),
                ) { Icon(Icons.Default.MoreVert, contentDescription = "Actions for ${view.name}") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (!view.isDefault) {
                        DropdownMenuItem(
                            text = { Text("Set as default") },
                            onClick = { menuOpen = false; onSetDefault() },
                            modifier = Modifier.testTag(ListViewSwitcherTestTags.setDefault(view.id)),
                        )
                    }
                    if (canFork) {
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text("Make a personal copy")
                                    Text(
                                        text = "Copies this view to your own. The shared one is untouched.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            onClick = { menuOpen = false; onFork() },
                            modifier = Modifier.testTag(ListViewSwitcherTestTags.fork(view.id)),
                        )
                    }
                    // Renaming and deleting belong to whoever created the view.
                    if (canModify) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = { menuOpen = false; onRename() },
                            modifier = Modifier.testTag(ListViewSwitcherTestTags.rename(view.id)),
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = { menuOpen = false; onDelete() },
                            modifier = Modifier.testTag(ListViewSwitcherTestTags.delete(view.id)),
                        )
                    }
                }
            }
        },
    )
}

/** Name + scope, the two things the API insists on when creating a view. */
@Composable
private fun CreateViewForm(
    isSaving: Boolean,
    onCreate: (String, ListViewScope) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var scope by remember { mutableStateOf(ListViewScope.PERSONAL) }
    Text("New view", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Name") },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ListViewSwitcherTestTags.CREATE_NAME),
    )
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ListViewScope.entries.forEach { option ->
            FilterChip(
                selected = scope == option,
                onClick = { scope = option },
                label = { Text(option.label) },
                modifier = Modifier.testTag(ListViewSwitcherTestTags.scope(option)),
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = {
            onCreate(name, scope)
            name = ""
        },
        enabled = name.isNotBlank() && !isSaving,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ListViewSwitcherTestTags.CREATE_SUBMIT),
    ) { Text("Create view") }
}

@Composable
private fun RenameViewDialog(
    view: ListView,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(view.id) { mutableStateOf(view.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(ListViewSwitcherTestTags.RENAME_DIALOG),
        title = { Text("Rename view") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.testTag(ListViewSwitcherTestTags.RENAME_FIELD),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag(ListViewSwitcherTestTags.RENAME_CONFIRM),
            ) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DefaultBadge() {
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text("Default") },
        leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) },
        colors = AssistChipDefaults.assistChipColors(),
    )
}

/**
 * How a view is configured, in words. Read from what the server stored — it
 * silently drops config values it does not recognise, so this is the truth about
 * the view rather than whatever was last sent.
 */
private fun viewSummary(view: ListView): String {
    val mode = view.config.storedMode?.replaceFirstChar { it.uppercase() } ?: view.config.mode.label
    return "$mode · ${view.config.density.label} · ${view.scope.label}"
}

private const val UNTITLED_VIEW = "Untitled view"

@Preview(showBackground = true)
@Composable
private fun ListViewSwitcherContentPreview() {
    val shared = ListView(
        id = "v1",
        listId = "L1",
        userId = "someone-else",
        name = "Roadmap",
        scope = ListViewScope.SHARED,
        config = ListViewConfig.DEFAULT,
        isDefault = true,
        position = 0,
    )
    val personal = shared.copy(
        id = "v2",
        userId = "me",
        name = "My cut",
        scope = ListViewScope.PERSONAL,
        isDefault = false,
    )
    InterlinedListTheme {
        ListViewSwitcherContent(
            state = ListViewsUiState(
                views = listOf(shared, personal),
                selectedViewId = "v1",
                currentUserId = "me",
                isLoading = false,
            ),
            onSelect = {},
            onSetDefault = {},
            onFork = {},
            onRename = {},
            onDelete = {},
            onCreate = { _, _ -> },
        )
    }
}
