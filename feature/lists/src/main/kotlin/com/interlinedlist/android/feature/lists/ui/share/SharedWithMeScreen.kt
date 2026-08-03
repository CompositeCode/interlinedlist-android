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
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.lists.domain.ShareRole
import com.interlinedlist.android.feature.lists.domain.SharedList

/** Stable test tags for the "Shared with me" screen. */
object SharedWithMeTestTags {
    const val LIST = "sharedWithMeList"
    const val EMPTY = "sharedWithMeEmpty"
    const val PROGRESS = "sharedWithMeProgress"
    const val ERROR = "sharedWithMeError"
    fun row(id: String) = "sharedWithMe_$id"
}

/**
 * Hilt-wired "Shared with me" surface: lists other users have granted the current
 * user access to. [onBack] pops navigation; [onOpenList] opens a shared list's detail.
 */
@Composable
fun SharedWithMeRoute(
    onBack: () -> Unit,
    onOpenList: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SharedWithMeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SharedWithMeScreen(
        state = state,
        onBack = onBack,
        onOpenList = onOpenList,
        modifier = modifier,
    )
}

/** Stateless "Shared with me" screen — owner + role per shared list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedWithMeScreen(
    state: SharedWithMeUiState,
    onBack: () -> Unit,
    onOpenList: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Shared with me") },
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
            ) { CircularProgressIndicator(Modifier.testTag(SharedWithMeTestTags.PROGRESS)) }

            state.errorMessage != null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = state.errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .padding(24.dp)
                        .testTag(SharedWithMeTestTags.ERROR),
                )
            }

            state.isEmpty -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.testTag(SharedWithMeTestTags.EMPTY),
                ) {
                    Text("Nothing shared with you yet", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Lists others share with you will appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .testTag(SharedWithMeTestTags.LIST),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.lists, key = { it.id }) { shared ->
                    SharedListRow(shared = shared, onOpen = { onOpenList(shared.id) })
                }
            }
        }
    }
}

@Composable
private fun SharedListRow(shared: SharedList, onOpen: () -> Unit) {
    Card(
        onClick = onOpen,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(SharedWithMeTestTags.row(shared.id)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = shared.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Shared by ${shared.ownerLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AssistChip(onClick = onOpen, label = { Text(shared.role.label) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
private fun SharedWithMePreview() {
    InterlinedListTheme {
        SharedWithMeScreen(
            state = SharedWithMeUiState(
                lists = listOf(
                    SharedList("w1", "Shows Upcoming & Seen", null, "Adron Hall", ShareRole.EDIT, true),
                    SharedList("w2", "Videos to Watch", null, "Adron Hall", ShareRole.VIEW, true),
                ),
                isLoading = false,
            ),
            onBack = {},
            onOpenList = {},
        )
    }
}
