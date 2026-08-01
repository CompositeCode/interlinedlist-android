package com.interlinedlist.android.feature.lists.ui.share

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.lists.domain.ListRow
import com.interlinedlist.android.feature.lists.domain.ShareRole
import com.interlinedlist.android.feature.lists.domain.SharedListResolution

/** Stable test tags for the resolve/claim shared-list screen. */
object SharedListTestTags {
    const val PROGRESS = "sharedListProgress"
    const val ERROR = "sharedListError"
    const val TITLE = "sharedListTitle"
    const val PREVIEW = "sharedListPreview"
    const val CLAIM = "sharedListClaim"
    const val CLAIMED = "sharedListClaimed"
}

/**
 * Hilt-wired resolve/claim screen for a `…/shared/{token}` link. Reads the token
 * from the nav SavedStateHandle (see [SHARED_TOKEN_ARG]); [onBack] pops navigation.
 */
@Composable
fun SharedListRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SharedListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SharedListScreen(
        state = state,
        onBack = onBack,
        onClaim = viewModel::claim,
        modifier = modifier,
    )
}

/** Stateless read-only preview of a shared list, with an optional "Claim access" action. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedListScreen(
    state: SharedListUiState,
    onBack: () -> Unit,
    onClaim: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Shared list") },
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
            ) { CircularProgressIndicator(Modifier.testTag(SharedListTestTags.PROGRESS)) }

            state.resolution == null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = state.errorMessage ?: "This link could not be opened.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .padding(24.dp)
                        .testTag(SharedListTestTags.ERROR),
                )
            }

            else -> SharedListBody(
                state = state,
                resolution = state.resolution,
                onClaim = onClaim,
                contentPadding = padding,
            )
        }
    }
}

@Composable
private fun SharedListBody(
    state: SharedListUiState,
    resolution: SharedListResolution,
    onClaim: () -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .testTag(SharedListTestTags.PREVIEW),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text(
                    text = resolution.title.ifBlank { "Untitled list" },
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.testTag(SharedListTestTags.TITLE),
                )
                if (resolution.ownerName != null) {
                    Text(
                        text = "Shared by ${resolution.ownerName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!resolution.description.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(resolution.description, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Access: ${resolution.role.label}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        item {
            when {
                state.claimed -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.testTag(SharedListTestTags.CLAIMED),
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Access claimed. This list is now in your account.")
                }

                state.canClaim -> Button(
                    onClick = onClaim,
                    enabled = !state.isClaiming,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(SharedListTestTags.CLAIM),
                ) { Text("Claim ${resolution.role.label.lowercase()} access") }
            }
            if (state.errorMessage != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (resolution.rows.isNotEmpty()) {
            item { Text("Preview", style = MaterialTheme.typography.titleMedium) }
            items(resolution.rows, key = { it.id }) { row ->
                PreviewRow(row)
            }
        }
    }
}

@Composable
private fun PreviewRow(row: ListRow) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            row.values.entries.take(4).forEach { (key, value) ->
                Text(
                    text = "$key: $value",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SharedListPreview() {
    InterlinedListTheme {
        SharedListScreen(
            state = SharedListUiState(
                resolution = SharedListResolution(
                    token = "tok",
                    listId = "L5",
                    title = "Public Reading",
                    description = "A shared reading list",
                    ownerName = "Grace H",
                    role = ShareRole.EDIT,
                    rows = listOf(ListRow("r1", mapOf("title" to "Dune", "pages" to "412"))),
                ),
                isLoading = false,
            ),
            onBack = {},
            onClaim = {},
        )
    }
}
