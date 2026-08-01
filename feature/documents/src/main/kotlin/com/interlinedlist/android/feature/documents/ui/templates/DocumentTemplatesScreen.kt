package com.interlinedlist.android.feature.documents.ui.templates

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.documents.domain.DocumentTemplate

/** Stable test tags for the templates surface. */
object DocumentTemplatesTestTags {
    const val LIST = "templatesList"
    const val EMPTY = "templatesEmpty"
    const val PROGRESS = "templatesProgress"
    const val ERROR = "templatesError"
    const val SEED_BUTTON = "templatesSeedButton"
    const val SUBSCRIPTION_GATE = "templatesSubscriptionGate"
    fun row(id: String) = "templateRow_$id"
}

/**
 * Hilt-wired templates route. Reads its optional target folder from the nav
 * SavedStateHandle (see [TEMPLATES_TARGET_FOLDER_ARG]). [onOpenDocument] receives the
 * id of a document freshly created from a template so the caller can open it.
 */
@Composable
fun DocumentTemplatesRoute(
    onOpenDocument: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DocumentTemplatesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DocumentTemplatesScreen(
        state = state,
        onSeedDefaults = viewModel::seedDefaults,
        onUseTemplate = { template -> viewModel.createFromTemplate(template, onOpenDocument) },
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentTemplatesScreen(
    state: DocumentTemplatesUiState,
    onSeedDefaults: () -> Unit,
    onUseTemplate: (DocumentTemplate) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Templates") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.subscriptionRequired -> SubscriptionGate(
                message = state.errorMessage,
                modifier = Modifier.padding(padding),
            )

            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(Modifier.testTag(DocumentTemplatesTestTags.PROGRESS))
            }

            else -> TemplatesContent(
                state = state,
                onSeedDefaults = onSeedDefaults,
                onUseTemplate = onUseTemplate,
                contentPadding = padding,
            )
        }
    }
}

@Composable
private fun TemplatesContent(
    state: DocumentTemplatesUiState,
    onSeedDefaults: () -> Unit,
    onUseTemplate: (DocumentTemplate) -> Unit,
    contentPadding: PaddingValues,
) {
    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        if (state.errorMessage != null && !state.subscriptionRequired) {
            Text(
                text = state.errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag(DocumentTemplatesTestTags.ERROR),
            )
        }

        if (state.templates.isEmpty()) {
            EmptyTemplates(
                canSeed = state.canSeedDefaults,
                isSeeding = state.isSeeding,
                onSeedDefaults = onSeedDefaults,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag(DocumentTemplatesTestTags.LIST),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(state.templates, key = { it.id }) { template ->
                    TemplateRow(template = template, onClick = { onUseTemplate(template) })
                }
            }
        }
    }
}

@Composable
private fun EmptyTemplates(
    canSeed: Boolean,
    isSeeding: Boolean,
    onSeedDefaults: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize().padding(24.dp).testTag(DocumentTemplatesTestTags.EMPTY),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "No templates yet",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Seed the built-in defaults to start with ready-made documents.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onSeedDefaults,
                enabled = canSeed,
                modifier = Modifier.testTag(DocumentTemplatesTestTags.SEED_BUTTON),
            ) {
                if (isSeeding) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Seed default templates")
                }
            }
        }
    }
}

@Composable
private fun TemplateRow(template: DocumentTemplate, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(DocumentTemplatesTestTags.row(template.id))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            Icons.Default.Description,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(Modifier.weight(1f)) {
            Text(
                template.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (template.snippet.isNotBlank()) {
                Text(
                    text = template.snippet,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SubscriptionGate(message: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().padding(24.dp).testTag(DocumentTemplatesTestTags.SUBSCRIPTION_GATE),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Subscriber feature",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message ?: "Templates require an active subscription.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DocumentTemplatesPreview() {
    InterlinedListTheme {
        DocumentTemplatesScreen(
            state = DocumentTemplatesUiState(
                isLoading = false,
                templates = listOf(
                    DocumentTemplate("t1", "Recipe", "Ingredients and steps"),
                    DocumentTemplate("t2", "Social Media Campaign", "Plan your posts"),
                ),
            ),
            onSeedDefaults = {},
            onUseTemplate = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DocumentTemplatesEmptyPreview() {
    InterlinedListTheme {
        DocumentTemplatesScreen(
            state = DocumentTemplatesUiState(isLoading = false, templates = emptyList()),
            onSeedDefaults = {},
            onUseTemplate = {},
            onBack = {},
        )
    }
}
