package com.interlinedlist.android.feature.profile.ui.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import com.interlinedlist.android.feature.profile.domain.PublicDocumentDetail

/** Stable test tags for the read-only public document view. */
object PublicDocumentTestTags {
    const val TITLE = "publicDocumentTitle"
    const val CONTENT = "publicDocumentContent"
    const val PROGRESS = "publicDocumentProgress"
    const val ERROR = "publicDocumentError"
    const val BACK = "publicDocumentBack"
}

/**
 * A read-only view of a public document (route `publicDocument/{documentId}`). Renders
 * the document's raw content as plain text (no editing).
 *
 * @param onBack pop back to the profile.
 */
@Composable
fun PublicDocumentRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PublicDocumentViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PublicDocumentScreen(state = state, onBack = onBack, onRetry = viewModel::refresh, modifier = modifier)
}

/** Stateless read-only document UI. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PublicDocumentScreen(
    state: PublicDocumentUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.document?.displayTitle ?: "Document",
                        modifier = Modifier.testTag(PublicDocumentTestTags.TITLE),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(PublicDocumentTestTags.BACK)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading && state.document == null -> CircularProgressIndicator(
                    Modifier.align(Alignment.Center).testTag(PublicDocumentTestTags.PROGRESS),
                )

                state.document == null -> Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = state.errorMessage ?: "Couldn't load this document.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag(PublicDocumentTestTags.ERROR),
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRetry) { Text("Retry") }
                }

                else -> Text(
                    text = state.document.content.ifBlank { "This document is empty." },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp)
                        .testTag(PublicDocumentTestTags.CONTENT),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PublicDocumentScreenPreview() {
    InterlinedListTheme {
        PublicDocumentScreen(
            state = PublicDocumentUiState(
                document = PublicDocumentDetail(
                    id = "d1",
                    title = "Design notes",
                    content = "# Heading\n\nSome body text goes here.",
                ),
                isLoading = false,
            ),
            onBack = {},
            onRetry = {},
        )
    }
}
