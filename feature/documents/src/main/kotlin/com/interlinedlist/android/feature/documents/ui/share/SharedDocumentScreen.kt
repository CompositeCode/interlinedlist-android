package com.interlinedlist.android.feature.documents.ui.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
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
import com.interlinedlist.android.feature.documents.domain.ShareRole
import com.interlinedlist.android.feature.documents.domain.SharedDocument
import com.interlinedlist.android.feature.documents.ui.common.MarkdownText

/** Stable test tags for the resolve/claim shared-document screen. */
object SharedDocumentTestTags {
    const val PROGRESS = "sharedDocProgress"
    const val ERROR = "sharedDocError"
    const val TITLE = "sharedDocTitle"
    const val PREVIEW = "sharedDocPreview"
    const val CLAIM = "sharedDocClaim"
    const val CLAIMED = "sharedDocClaimed"
}

/**
 * Hilt-wired resolve/claim screen for a `documents/shared/{token}` link. Reads the
 * token from the nav SavedStateHandle (see [SHARED_DOCUMENT_TOKEN_ARG]); [onBack]
 * pops navigation.
 */
@Composable
fun SharedDocumentRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SharedDocumentViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SharedDocumentScreen(
        state = state,
        onBack = onBack,
        onClaim = viewModel::claim,
        modifier = modifier,
    )
}

/** Stateless read-only preview of a shared document, with an optional "Claim access" action. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedDocumentScreen(
    state: SharedDocumentUiState,
    onBack: () -> Unit,
    onClaim: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Shared document") },
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
            ) { CircularProgressIndicator(Modifier.testTag(SharedDocumentTestTags.PROGRESS)) }

            state.document == null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = state.errorMessage ?: "This link could not be opened.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .padding(24.dp)
                        .testTag(SharedDocumentTestTags.ERROR),
                )
            }

            else -> SharedDocumentBody(
                state = state,
                document = state.document,
                onClaim = onClaim,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        }
    }
}

@Composable
private fun SharedDocumentBody(
    state: SharedDocumentUiState,
    document: SharedDocument,
    onClaim: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(16.dp)
            .testTag(SharedDocumentTestTags.PREVIEW),
    ) {
        Text(
            text = document.title.ifBlank { "Untitled document" },
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.testTag(SharedDocumentTestTags.TITLE),
        )
        if (document.ownerName != null) {
            Text(
                text = "Shared by ${document.ownerName}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Access: ${document.role.label}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )

        Spacer(Modifier.height(12.dp))
        when {
            state.claimed -> androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.testTag(SharedDocumentTestTags.CLAIMED),
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Access claimed. This document is now in your account.")
            }

            state.canClaim -> Button(
                onClick = onClaim,
                enabled = !state.isClaiming,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(SharedDocumentTestTags.CLAIM),
            ) { Text("Claim ${document.role.label.lowercase()} access") }
        }
        if (state.errorMessage != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = state.errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(16.dp))
        // MarkdownText brings its own vertical scroll, so it owns the remaining space.
        MarkdownText(
            markdown = document.content.orEmpty().ifBlank { "_This document has no content._" },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SharedDocumentPreview() {
    InterlinedListTheme {
        SharedDocumentScreen(
            state = SharedDocumentUiState(
                document = SharedDocument(
                    token = "tok",
                    documentId = "D5",
                    title = "Public Notes",
                    content = "# Heading\n\nSome shared **markdown** content.",
                    ownerName = "Grace H",
                    role = ShareRole.EDIT,
                ),
                isLoading = false,
            ),
            onBack = {},
            onClaim = {},
        )
    }
}
