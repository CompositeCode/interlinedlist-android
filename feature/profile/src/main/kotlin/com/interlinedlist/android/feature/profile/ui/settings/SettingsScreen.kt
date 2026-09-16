package com.interlinedlist.android.feature.profile.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.profile.domain.SettingsBounds
import com.interlinedlist.android.feature.profile.domain.UserSettings
import com.interlinedlist.android.feature.profile.domain.ViewingPreference
import com.interlinedlist.android.feature.profile.domain.defaultPubliclyVisibleOrDefault
import com.interlinedlist.android.feature.profile.domain.maxMessageLengthOrDefault
import com.interlinedlist.android.feature.profile.domain.messagesPerPageOrDefault
import com.interlinedlist.android.feature.profile.domain.showAdvancedPostSettingsOrDefault

/** Stable test tags for the Settings screen. */
object SettingsTestTags {
    const val BACK = "settingsBack"
    const val PROGRESS = "settingsProgress"
    const val ERROR = "settingsError"
    const val DISMISS_ERROR = "settingsDismissError"
    const val RETRY = "settingsRetry"
    const val PROFILE = "settingsGroupProfile"
    const val VIEW_PREFERENCES = "settingsGroupViewPreferences"
    const val MESSAGE_SETTINGS = "settingsGroupMessageSettings"
    const val SHOW_PREVIEWS = "settingsShowPreviews"
    const val MAX_MESSAGE_LENGTH = "settingsMaxMessageLength"
    const val MESSAGES_PER_PAGE = "settingsMessagesPerPage"
    const val DEFAULT_PUBLICLY_VISIBLE = "settingsDefaultPubliclyVisible"
    const val SHOW_ADVANCED_POST_SETTINGS = "settingsShowAdvancedPostSettings"

    /** Tag for one feed-filter option, keyed on its wire value. */
    fun viewingPreference(option: ViewingPreference): String =
        "settingsViewingPreference_${option.wire}"
}

/** The label shown for each feed filter (wording follows the web help centre). */
private fun ViewingPreference.label(): String = when (this) {
    ViewingPreference.ALL -> "All messages"
    ViewingPreference.MINE -> "My messages"
    ViewingPreference.FOLLOWING -> "Following only"
    ViewingPreference.FOLLOWERS -> "Followers only"
}

/** The one-line explanation shown under each feed filter. */
private fun ViewingPreference.description(): String = when (this) {
    ViewingPreference.ALL -> "Your messages plus all public messages"
    ViewingPreference.MINE -> "Only your own messages"
    ViewingPreference.FOLLOWING -> "Messages from people you follow, plus your own"
    ViewingPreference.FOLLOWERS -> "Messages from people who follow you, plus your own"
}

/**
 * Hilt-wired Settings entry point (route `settings`), reached from the Account hub.
 * Mirrors the web Settings page: titled groups of preferences that save as you change
 * them. Only the groups the app supports are rendered.
 *
 * @param onBack pops Settings off the back stack.
 */
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::refresh,
        onSelectViewingPreference = viewModel::setViewingPreference,
        onToggleShowPreviews = viewModel::setShowPreviews,
        onSetMessagesPerPage = viewModel::setMessagesPerPage,
        onSetMaxMessageLength = viewModel::setMaxMessageLength,
        onToggleDefaultPubliclyVisible = viewModel::setDefaultPubliclyVisible,
        onToggleShowAdvancedPostSettings = viewModel::setShowAdvancedPostSettings,
        onDismissError = viewModel::dismissError,
        modifier = modifier,
    )
}

/** Stateless Settings UI — one titled group per area of the web Settings page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSelectViewingPreference: (ViewingPreference) -> Unit,
    onToggleShowPreviews: (Boolean) -> Unit,
    onSetMessagesPerPage: (Int) -> Unit,
    onSetMaxMessageLength: (Int) -> Unit,
    onToggleDefaultPubliclyVisible: (Boolean) -> Unit,
    onToggleShowAdvancedPostSettings: (Boolean) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(SettingsTestTags.BACK)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val settings = state.settings
        when {
            settings != null -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (state.errorMessage != null) {
                    SaveErrorBanner(message = state.errorMessage, onDismiss = onDismissError)
                }
                ProfileGroup(
                    settings = settings,
                    onSetMaxMessageLength = onSetMaxMessageLength,
                )
                ViewPreferencesGroup(
                    settings = settings,
                    onSelectViewingPreference = onSelectViewingPreference,
                    onToggleShowPreviews = onToggleShowPreviews,
                    onSetMessagesPerPage = onSetMessagesPerPage,
                )
                MessageSettingsGroup(
                    settings = settings,
                    onToggleDefaultPubliclyVisible = onToggleDefaultPubliclyVisible,
                    onToggleShowAdvancedPostSettings = onToggleShowAdvancedPostSettings,
                )
                Spacer(Modifier.height(24.dp))
            }

            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(Modifier.testTag(SettingsTestTags.PROGRESS))
            }

            else -> Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.errorMessage ?: "Couldn't load your settings.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag(SettingsTestTags.ERROR),
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRetry, modifier = Modifier.testTag(SettingsTestTags.RETRY)) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}

/**
 * "View preferences": which messages the Home feed shows, and whether link-preview
 * cards render at all.
 */
@Composable
private fun ViewPreferencesGroup(
    settings: UserSettings,
    onSelectViewingPreference: (ViewingPreference) -> Unit,
    onToggleShowPreviews: (Boolean) -> Unit,
    onSetMessagesPerPage: (Int) -> Unit,
) {
    SettingsGroup(
        title = "View preferences",
        description = "Control what appears in your Home feed.",
        modifier = Modifier.testTag(SettingsTestTags.VIEW_PREFERENCES),
    ) {
        ViewingPreference.entries.forEach { option ->
            SettingsRadioRow(
                label = option.label(),
                description = option.description(),
                selected = settings.viewingPreference == option,
                onSelect = { onSelectViewingPreference(option) },
                tag = SettingsTestTags.viewingPreference(option),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SettingsSwitchRow(
            label = "Show link previews",
            description = "Show preview cards for links in messages.",
            checked = settings.showPreviews,
            onCheckedChange = onToggleShowPreviews,
            tag = SettingsTestTags.SHOW_PREVIEWS,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        // The web keeps the page size here rather than under Message settings.
        SettingsNumberRow(
            label = "Messages per page",
            description = "How many messages to load at once (10 to 30).",
            value = settings.messagesPerPageOrDefault,
            range = SettingsBounds.MESSAGES_PER_PAGE,
            onValueChange = onSetMessagesPerPage,
            tag = SettingsTestTags.MESSAGES_PER_PAGE,
        )
    }
}

/**
 * "Profile": the account-wide message character limit.
 *
 * The web deliberately files the limit here and not under Message settings — its own
 * help centre tells users to "adjust it in Settings, then Profile (not Message
 * Settings)" — so this mirrors that. Message settings points at it for anyone who
 * looks there first.
 */
@Composable
private fun ProfileGroup(
    settings: UserSettings,
    onSetMaxMessageLength: (Int) -> Unit,
) {
    SettingsGroup(
        title = "Profile",
        description = "Your display name, bio and avatar are edited from Edit profile.",
        modifier = Modifier.testTag(SettingsTestTags.PROFILE),
    ) {
        SettingsNumberRow(
            label = "Message character limit",
            description = "The longest message you can post (default 666 characters).",
            value = settings.maxMessageLengthOrDefault,
            range = SettingsBounds.MAX_MESSAGE_LENGTH,
            onValueChange = onSetMaxMessageLength,
            tag = SettingsTestTags.MAX_MESSAGE_LENGTH,
            step = SettingsBounds.MAX_MESSAGE_LENGTH_STEP,
        )
    }
}

/**
 * "Message settings": how the composer starts out. The character limit and the feed
 * page size belong to the two groups above, matching the web, so the description
 * says where they went.
 */
@Composable
private fun MessageSettingsGroup(
    settings: UserSettings,
    onToggleDefaultPubliclyVisible: (Boolean) -> Unit,
    onToggleShowAdvancedPostSettings: (Boolean) -> Unit,
) {
    SettingsGroup(
        title = "Message settings",
        description = "How new messages start out. As on the web, the character limit is " +
            "under Profile and the page size under View preferences.",
        modifier = Modifier.testTag(SettingsTestTags.MESSAGE_SETTINGS),
    ) {
        SettingsSwitchRow(
            label = "Default message visibility",
            description = "New messages start public. Turn this off to start them private.",
            checked = settings.defaultPubliclyVisibleOrDefault,
            onCheckedChange = onToggleDefaultPubliclyVisible,
            tag = SettingsTestTags.DEFAULT_PUBLICLY_VISIBLE,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SettingsSwitchRow(
            label = "Advanced post settings",
            description = "Show the gear icon next to the message input for images, " +
                "video, and cross-posting.",
            checked = settings.showAdvancedPostSettingsOrDefault,
            onCheckedChange = onToggleShowAdvancedPostSettings,
            tag = SettingsTestTags.SHOW_ADVANCED_POST_SETTINGS,
        )
    }
}

/** A failed save reported inline above the groups, dismissible by the user. */
@Composable
private fun SaveErrorBanner(message: String, onDismiss: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 8.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f).testTag(SettingsTestTags.ERROR),
        )
        TextButton(onClick = onDismiss, modifier = Modifier.testTag(SettingsTestTags.DISMISS_ERROR)) {
            Text("Dismiss")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    InterlinedListTheme {
        SettingsScreen(
            state = SettingsUiState(
                settings = UserSettings(
                    maxMessageLength = 666,
                    defaultPubliclyVisible = true,
                    messagesPerPage = 20,
                    viewingPreference = ViewingPreference.FOLLOWING,
                    showPreviews = true,
                    showAdvancedPostSettings = false,
                ),
            ),
            onBack = {},
            onRetry = {},
            onSelectViewingPreference = {},
            onToggleShowPreviews = {},
            onSetMessagesPerPage = {},
            onSetMaxMessageLength = {},
            onToggleDefaultPubliclyVisible = {},
            onToggleShowAdvancedPostSettings = {},
            onDismissError = {},
        )
    }
}
