package com.interlinedlist.android.feature.integrations.ui.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.integrations.domain.PlanLimits

/** Stable test tags so UI/instrumented tests can address the hub controls. */
object IntegrationsHubTestTags {
    const val EXPORT = "hubExport"
    const val ACCOUNTS = "hubAccounts"
    const val LIMITS = "hubLimits"
}

/**
 * Public entry point for the integrations feature, reached from the Account hub.
 * This is the hub; it drills down into the "Export data" and "Connected accounts"
 * sub-screens and shows plan limits inline when available.
 *
 * @param onBack pop back to the Account hub.
 * @param onOpenExport navigate to the export sub-route (see ExportRoute).
 * @param onOpenConnectedAccounts navigate to the connected-accounts sub-route.
 */
@Composable
fun IntegrationsRoute(
    onBack: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenConnectedAccounts: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IntegrationsHubViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    IntegrationsHubScreen(
        state = state,
        onBack = onBack,
        onOpenExport = onOpenExport,
        onOpenConnectedAccounts = onOpenConnectedAccounts,
        modifier = modifier,
    )
}

/** Stateless integrations hub UI — easy to preview and to drive from Compose tests. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntegrationsHubScreen(
    state: IntegrationsHubUiState,
    onBack: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenConnectedAccounts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Integrations") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            item {
                HubEntry(
                    icon = Icons.Default.Download,
                    title = "Export data",
                    subtitle = "Download your follows, lists, messages and rows as CSV.",
                    onClick = onOpenExport,
                    testTag = IntegrationsHubTestTags.EXPORT,
                )
            }
            item {
                HubEntry(
                    icon = Icons.Default.Link,
                    title = "Connected accounts",
                    subtitle = "See which social accounts are linked.",
                    onClick = onOpenConnectedAccounts,
                    testTag = IntegrationsHubTestTags.ACCOUNTS,
                )
            }
            state.limits?.takeIf { it.limits.isNotEmpty() }?.let { limits ->
                item { LimitsCard(limits) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HubEntry(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag(testTag),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun LimitsCard(limits: PlanLimits) {
    Card(modifier = Modifier.fillMaxWidth().testTag(IntegrationsHubTestTags.LIMITS)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = limits.planName?.let { "Plan: $it" } ?: "Plan limits",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.size(8.dp))
            limits.limits.forEach { limit -> LimitRow(limit) }
        }
    }
}

@Composable
private fun LimitRow(limit: PlanLimits.Limit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(limit.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                text = when {
                    limit.isUnlimited -> limit.used?.let { "$it / ∞" } ?: "Unlimited"
                    else -> "${limit.used ?: 0} / ${limit.max}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val max = limit.max
        val used = limit.used
        if (max != null && max > 0 && used != null) {
            Spacer(Modifier.size(4.dp))
            LinearProgressIndicator(
                progress = { (used.toFloat() / max).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun IntegrationsHubScreenPreview() {
    InterlinedListTheme {
        IntegrationsHubScreen(
            state = IntegrationsHubUiState(
                isLoadingLimits = false,
                limits = PlanLimits(
                    planName = "Free",
                    limits = listOf(
                        PlanLimits.Limit("lists", "Lists", used = 3, max = 5),
                        PlanLimits.Limit("follows", "Follows", used = 42, max = null),
                    ),
                ),
            ),
            onBack = {},
            onOpenExport = {},
            onOpenConnectedAccounts = {},
        )
    }
}
