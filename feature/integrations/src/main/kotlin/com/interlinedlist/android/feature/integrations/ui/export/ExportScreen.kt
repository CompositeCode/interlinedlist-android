package com.interlinedlist.android.feature.integrations.ui.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.integrations.domain.ExportType

/** Stable test tags so UI/instrumented tests can address the export controls. */
object ExportTestTags {
    const val LIST = "exportList"
    const val ERROR = "exportError"
    fun row(type: ExportType) = "export_${type.pathSegment}"
    fun button(type: ExportType) = "exportButton_${type.pathSegment}"
    fun progress(type: ExportType) = "exportProgress_${type.pathSegment}"
}

/**
 * Hilt-wired entry point for the "Export data" screen. Collects state and, when a
 * download finishes, forwards the cached CSV to the Android share sheet.
 */
@Composable
fun ExportRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExportViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // One-shot: launch the share sheet each time a CSV is ready.
    LaunchedEffect(Unit) {
        viewModel.ready.collect { ready -> ExportSharing.share(context, ready.file) }
    }

    ExportScreen(
        state = state,
        onExport = viewModel::export,
        onDismissError = viewModel::clearError,
        onBack = onBack,
        modifier = modifier,
    )
}

/** Stateless "Export data" UI — easy to preview and to drive from Compose tests. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    state: ExportUiState,
    onExport: (ExportType) -> Unit,
    onDismissError: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            onDismissError()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Export data") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = {
            SnackbarHost(
                snackbarHostState,
                modifier = Modifier.testTag(ExportTestTags.ERROR),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .testTag(ExportTestTags.LIST),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
        ) {
            item {
                Text(
                    text = "Download your data as CSV, then save or send it from the share sheet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(ExportType.entries, key = { it.pathSegment }) { type ->
                ExportRow(
                    type = type,
                    isDownloading = state.isDownloading(type),
                    enabled = !state.isBusy,
                    onExport = { onExport(type) },
                )
            }
        }
    }
}

@Composable
private fun ExportRow(
    type: ExportType,
    isDownloading: Boolean,
    enabled: Boolean,
    onExport: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().testTag(ExportTestTags.row(type))) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(type.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    type.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OutlinedButton(
                onClick = onExport,
                enabled = enabled,
                modifier = Modifier.testTag(ExportTestTags.button(type)),
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp).testTag(ExportTestTags.progress(type)),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Export")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ExportScreenPreview() {
    InterlinedListTheme {
        ExportScreen(
            state = ExportUiState(downloading = ExportType.LISTS),
            onExport = {},
            onDismissError = {},
            onBack = {},
        )
    }
}
