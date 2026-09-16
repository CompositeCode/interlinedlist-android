package com.interlinedlist.android.feature.notifications.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.notifications.domain.NotificationChannel
import com.interlinedlist.android.feature.notifications.domain.NotificationPreference
import com.interlinedlist.android.feature.notifications.push.PushRegistrationViewModel
import com.interlinedlist.android.feature.notifications.push.shouldRequestPostNotifications

/** Stable test tags for the notification-preferences screen. */
object NotificationPreferencesTags {
    const val LIST = "notificationPreferencesList"
    const val EMPTY = "notificationPreferencesEmpty"
    const val ERROR = "notificationPreferencesError"
    const val PROGRESS = "notificationPreferencesProgress"
    const val BACK = "notificationPreferencesBack"

    /** Tag for a single channel toggle, unique per event + channel. */
    fun toggle(key: String, channel: NotificationChannel): String =
        "notificationPreferenceToggle_${key}_${channel.wireKey}"
}

/** Human-readable channel label shown next to each toggle. */
private fun NotificationChannel.displayLabel(): String = when (this) {
    NotificationChannel.PUSH -> "Push"
    NotificationChannel.IN_APP -> "In-app"
    NotificationChannel.EMAIL -> "Email"
}

/**
 * Hilt-wired notification-preferences entry point. Reached from the Account hub and
 * from the notifications tray as a drill-down; mirrors the back pattern used by the
 * other detail screens.
 *
 * This is also where `POST_NOTIFICATIONS` is requested — deliberately here and NOT on
 * cold start. Switching a "Push" channel on is the user asking to be notified, so the
 * system dialog lands in context (see `shouldRequestPostNotifications`). A denial is a
 * no-op for the rest of the app: the preference is still saved, the poll still runs and
 * the in-app tray is unaffected; only the device-token registration stays on hold.
 *
 * @param onBack pops the preferences screen off the back stack.
 */
@Composable
fun NotificationPreferencesRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationPreferencesViewModel = hiltViewModel(),
    pushRegistrationViewModel: PushRegistrationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val requestPostNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        // Granted: the token can be registered now — it has not changed, so the token
        // flow will not re-emit and the manager has to be nudged. Denied: nothing to do.
        if (granted) pushRegistrationViewModel.onNotificationPermissionGranted()
    }
    NotificationPreferencesScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::refresh,
        onToggle = { key, channel, enabled ->
            viewModel.onToggle(key, channel, enabled)
            val granted = ContextCompat.checkSelfPermission(
                context,
                POST_NOTIFICATIONS_PERMISSION,
            ) == PackageManager.PERMISSION_GRANTED
            if (shouldRequestPostNotifications(channel, enabled, alreadyGranted = granted)) {
                requestPostNotifications.launch(POST_NOTIFICATIONS_PERMISSION)
            }
        },
        modifier = modifier,
    )
}

/** The Android 13+ runtime permission guarding tray notifications. */
private const val POST_NOTIFICATIONS_PERMISSION = Manifest.permission.POST_NOTIFICATIONS

/** Stateless preferences UI — drives the list/empty/error/loading states from [state]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationPreferencesScreen(
    state: NotificationPreferencesUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onToggle: (key: String, channel: NotificationChannel, enabled: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Notification preferences") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag(NotificationPreferencesTags.BACK),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Content(
            state = state,
            contentPadding = padding,
            onRetry = onRetry,
            onToggle = onToggle,
        )
    }
}

@Composable
private fun Content(
    state: NotificationPreferencesUiState,
    contentPadding: PaddingValues,
    onRetry: () -> Unit,
    onToggle: (key: String, channel: NotificationChannel, enabled: Boolean) -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        when {
            state.isEmpty && state.isLoading -> LoadingState()
            state.isEmpty && state.errorMessage != null -> ErrorState(state.errorMessage, onRetry)
            state.isEmpty -> EmptyState()
            else -> PreferenceList(state = state, onToggle = onToggle)
        }
    }
}

@Composable
private fun PreferenceList(
    state: NotificationPreferencesUiState,
    onToggle: (key: String, channel: NotificationChannel, enabled: Boolean) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag(NotificationPreferencesTags.LIST),
    ) {
        items(state.preferences, key = { it.key }) { preference ->
            PreferenceRow(preference = preference, onToggle = onToggle)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/**
 * A single event: its label + description, and one [Switch] per AVAILABLE channel.
 * Only the channels the event actually offers are rendered — the set varies per event.
 */
@Composable
private fun PreferenceRow(
    preference: NotificationPreference,
    onToggle: (key: String, channel: NotificationChannel, enabled: Boolean) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = preference.label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (preference.description.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = preference.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        preference.availableChannels.forEach { channel ->
            ChannelToggleRow(
                eventKey = preference.key,
                channel = channel,
                enabled = preference.isEnabled(channel),
                onToggle = onToggle,
            )
        }
    }
}

@Composable
private fun ChannelToggleRow(
    eventKey: String,
    channel: NotificationChannel,
    enabled: Boolean,
    onToggle: (key: String, channel: NotificationChannel, enabled: Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = channel.displayLabel(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = enabled,
            onCheckedChange = { onToggle(eventKey, channel, it) },
            modifier = Modifier
                .testTag(NotificationPreferencesTags.toggle(eventKey, channel))
                .semantics { contentDescription = "${channel.displayLabel()} for $eventKey" },
        )
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.testTag(NotificationPreferencesTags.PROGRESS))
    }
}

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = "No notification preferences to configure.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(NotificationPreferencesTags.EMPTY),
        )
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag(NotificationPreferencesTags.ERROR),
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}

@Preview(showBackground = true)
@Composable
private fun NotificationPreferencesPreview() {
    InterlinedListTheme {
        NotificationPreferencesScreen(
            state = NotificationPreferencesUiState(
                preferences = listOf(
                    NotificationPreference(
                        key = "dig",
                        label = "Digs on your messages",
                        description = "When someone presses “I Dig!” on one of your messages.",
                        channels = mapOf(
                            NotificationChannel.PUSH to true,
                            NotificationChannel.IN_APP to true,
                        ),
                    ),
                    NotificationPreference(
                        key = "follow",
                        label = "New followers & follow requests",
                        description = "When someone follows you or requests to follow you.",
                        channels = mapOf(
                            NotificationChannel.PUSH to true,
                            NotificationChannel.EMAIL to false,
                        ),
                    ),
                    NotificationPreference(
                        key = "reply",
                        label = "Replies",
                        description = "When someone replies to your message.",
                        channels = mapOf(NotificationChannel.EMAIL to true),
                    ),
                ),
            ),
            onBack = {},
            onRetry = {},
            onToggle = { _, _, _ -> },
        )
    }
}
