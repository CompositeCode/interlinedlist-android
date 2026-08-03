package com.interlinedlist.android.feature.directmessages.ui.newmessage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.interlinedlist.android.feature.directmessages.data.Recipient
import com.interlinedlist.android.feature.directmessages.ui.DmAvatar

/** Stable test tags for the recipient picker. */
object NewMessageTestTags {
    const val SEARCH = "dmNewSearch"
    const val LIST = "dmNewList"
    const val EMPTY = "dmNewEmpty"
    const val BACK = "dmNewBack"
    fun row(username: String) = "dmNewRow_$username"
}

/** Hilt-wired entry point for the recipient picker. */
@Composable
fun NewMessageRoute(
    onBack: () -> Unit,
    onRecipientChosen: (username: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NewMessageViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    NewMessageScreen(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onBack = onBack,
        onRecipientChosen = { onRecipientChosen(it.username) },
        modifier = modifier,
    )
}

/** Stateless recipient-picker UI, driveable directly from Compose tests. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewMessageScreen(
    state: NewMessageUiState,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onRecipientChosen: (Recipient) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("New message") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag(NewMessageTestTags.BACK),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                singleLine = true,
                placeholder = { Text("Search people") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag(NewMessageTestTags.SEARCH),
            )
            if (state.isEmpty) {
                EmptyRecipients(modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(NewMessageTestTags.LIST),
                ) {
                    items(state.filtered, key = { it.username }) { recipient ->
                        RecipientRow(
                            recipient = recipient,
                            onClick = { onRecipientChosen(recipient) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun RecipientRow(
    recipient: Recipient,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(NewMessageTestTags.row(recipient.username))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DmAvatar(
            avatarUrl = recipient.avatarUrl,
            fallbackText = recipient.displayName ?: recipient.username,
            size = 40,
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = recipient.displayName ?: recipient.username,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "@${recipient.username}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyRecipients(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.testTag(NewMessageTestTags.EMPTY),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "No one to message yet",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "Follow people to start conversations.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
