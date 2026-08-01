package com.interlinedlist.android.feature.billing.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme

/** Stable test tags so UI/instrumented tests can address the upsell controls. */
object UpsellTestTags {
    const val SUBSCRIBE = "upsellSubscribe"
    const val MANAGE = "upsellManage"
    const val ERROR = "upsellError"
}

/** The benefit bullets shown on the upsell — kept here so the screen stays declarative. */
private val benefits = listOf(
    "Unlimited lists and data rows",
    "Full CSV exports of your data",
    "Priority access to new integrations",
)

/**
 * Hilt-wired entry point for the subscription upsell. Collects state and, when a
 * Stripe session URL is ready, opens it in the browser with a plain
 * `ACTION_VIEW` intent (no Custom Tabs dependency).
 */
@Composable
fun UpsellRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UpsellViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // One-shot: open each Stripe hosted URL as it becomes ready.
    LaunchedEffect(Unit) {
        viewModel.open.collect { event ->
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(event.url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    UpsellScreen(
        state = state,
        onSubscribe = { viewModel.subscribe() },
        onManage = viewModel::manageSubscription,
        onDismissError = viewModel::clearError,
        onBack = onBack,
        modifier = modifier,
    )
}

/** Stateless upsell UI — easy to preview and to drive from Compose tests. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpsellScreen(
    state: UpsellUiState,
    onSubscribe: () -> Unit,
    onManage: () -> Unit,
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
                title = { Text("Upgrade") },
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
                modifier = Modifier.testTag(UpsellTestTags.ERROR),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.size(8.dp))
            Text(
                text = "Go Pro",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "Unlock the full InterlinedList experience with a subscription.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                benefits.forEach { benefit -> BenefitRow(benefit) }
            }

            Spacer(Modifier.size(8.dp))

            Button(
                onClick = onSubscribe,
                enabled = !state.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(UpsellTestTags.SUBSCRIBE),
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Subscribe")
                }
            }

            OutlinedButton(
                onClick = onManage,
                enabled = !state.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(UpsellTestTags.MANAGE),
            ) {
                Text("Manage subscription")
            }

            Text(
                text = "Already subscribed? Open the customer portal to update or cancel " +
                    "your plan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BenefitRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(12.dp))
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Preview(showBackground = true)
@Composable
private fun UpsellScreenPreview() {
    InterlinedListTheme {
        UpsellScreen(
            state = UpsellUiState(),
            onSubscribe = {},
            onManage = {},
            onDismissError = {},
            onBack = {},
        )
    }
}
