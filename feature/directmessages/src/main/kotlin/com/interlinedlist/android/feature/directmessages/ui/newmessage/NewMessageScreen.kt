package com.interlinedlist.android.feature.directmessages.ui.newmessage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonAddAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextAlign
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
    const val FIND_PEOPLE = "dmNewFindPeople"
    const val NO_MATCHES = "dmNewNoMatches"
    const val PROGRESS = "dmNewProgress"
    const val ERROR = "dmNewError"
    const val RETRY = "dmNewRetry"
    const val BACK = "dmNewBack"
    fun row(username: String) = "dmNewRow_$username"
}

/** Hilt-wired entry point for the recipient picker. */
@Composable
fun NewMessageRoute(
    onBack: () -> Unit,
    onRecipientChosen: (username: String) -> Unit,
    onFindPeople: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NewMessageViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    NewMessageScreen(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onBack = onBack,
        onRecipientChosen = { onRecipientChosen(it.username) },
        onFindPeople = onFindPeople,
        onRetry = viewModel::load,
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
    onFindPeople: () -> Unit,
    onRetry: () -> Unit,
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
            // Searching is only meaningful once there is something to search.
            if (state.content == NewMessageContent.Recipients ||
                state.content == NewMessageContent.NoMatches
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
            }
            when (state.content) {
                NewMessageContent.Loading -> Centered {
                    CircularProgressIndicator(
                        modifier = Modifier.testTag(NewMessageTestTags.PROGRESS),
                    )
                }

                NewMessageContent.Error -> Centered {
                    ErrorRecipients(
                        message = state.errorMessage.orEmpty(),
                        onRetry = onRetry,
                    )
                }

                NewMessageContent.NoRecipients -> Centered {
                    NoRecipients(onFindPeople = onFindPeople)
                }

                NewMessageContent.NoMatches -> Centered {
                    Text(
                        text = RecipientRuleCopy.NO_MATCHES,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag(NewMessageTestTags.NO_MATCHES),
                    )
                }

                NewMessageContent.Recipients -> LazyColumn(
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

/** Fills the remaining space and centres [content]; used by the non-list states. */
@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
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

/**
 * Loaded, but the user may not message anyone yet. This is the normal state for
 * a new account, so it explains the rule (see [RecipientRuleCopy]) and offers a
 * way out instead of leaving the user on a blank list.
 */
@Composable
private fun NoRecipients(onFindPeople: () -> Unit) {
    Column(
        modifier = Modifier.testTag(NewMessageTestTags.EMPTY),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.PersonAddAlt,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = RecipientRuleCopy.TITLE,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = RecipientRuleCopy.EXPLANATION,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onFindPeople,
            modifier = Modifier.testTag(NewMessageTestTags.FIND_PEOPLE),
        ) {
            Text(RecipientRuleCopy.FIND_PEOPLE)
        }
    }
}

/** A genuine failure to load the recipient set — retryable, never the rule copy. */
@Composable
private fun ErrorRecipients(message: String, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(NewMessageTestTags.ERROR),
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry, modifier = Modifier.testTag(NewMessageTestTags.RETRY)) {
            Text("Retry")
        }
    }
}
