package com.interlinedlist.android.feature.profile.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.profile.domain.UserSettings
import com.interlinedlist.android.feature.profile.domain.ViewingPreference
import com.interlinedlist.android.feature.profile.ui.settings.SettingsScreen
import com.interlinedlist.android.feature.profile.ui.settings.SettingsTestTags
import com.interlinedlist.android.feature.profile.ui.settings.SettingsUiState
import com.interlinedlist.android.feature.profile.ui.settings.settingsDecrementTag
import com.interlinedlist.android.feature.profile.ui.settings.settingsIncrementTag
import com.interlinedlist.android.feature.profile.ui.settings.settingsNumberErrorTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(
        state: SettingsUiState,
        onSelectViewingPreference: (ViewingPreference) -> Unit = {},
        onToggleShowPreviews: (Boolean) -> Unit = {},
        onSetMessagesPerPage: (Int) -> Unit = {},
        onSetMaxMessageLength: (Int) -> Unit = {},
        onToggleDefaultPubliclyVisible: (Boolean) -> Unit = {},
        onToggleShowAdvancedPostSettings: (Boolean) -> Unit = {},
        onRetry: () -> Unit = {},
        onDismissError: () -> Unit = {},
    ) {
        composeRule.setContent {
            InterlinedListTheme {
                SettingsScreen(
                    state = state,
                    onBack = {},
                    onRetry = onRetry,
                    onSelectViewingPreference = onSelectViewingPreference,
                    onToggleShowPreviews = onToggleShowPreviews,
                    onSetMessagesPerPage = onSetMessagesPerPage,
                    onSetMaxMessageLength = onSetMaxMessageLength,
                    onToggleDefaultPubliclyVisible = onToggleDefaultPubliclyVisible,
                    onToggleShowAdvancedPostSettings = onToggleShowAdvancedPostSettings,
                    onDismissError = onDismissError,
                )
            }
        }
    }

    @Test
    fun viewPreferences_showsTheStoredSelectionAndPreviewToggle() {
        setContent(
            SettingsUiState(
                settings = UserSettings(
                    viewingPreference = ViewingPreference.FOLLOWING,
                    showPreviews = false,
                ),
            ),
        )

        composeRule.onNodeWithTag(SettingsTestTags.VIEW_PREFERENCES).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.viewingPreference(ViewingPreference.FOLLOWING))
            .assertIsSelected()
        composeRule.onNodeWithTag(SettingsTestTags.SHOW_PREVIEWS).assertIsOff()
    }

    @Test
    fun choosingAnotherViewPreference_reportsTheSelection() {
        var chosen: ViewingPreference? = null
        setContent(
            state = SettingsUiState(settings = UserSettings(viewingPreference = ViewingPreference.ALL)),
            onSelectViewingPreference = { chosen = it },
        )

        composeRule.onNodeWithTag(SettingsTestTags.viewingPreference(ViewingPreference.FOLLOWERS))
            .performClick()

        assert(chosen == ViewingPreference.FOLLOWERS)
    }

    @Test
    fun togglingLinkPreviews_reportsTheNewValue() {
        var toggled: Boolean? = null
        setContent(
            state = SettingsUiState(settings = UserSettings(showPreviews = true)),
            onToggleShowPreviews = { toggled = it },
        )

        composeRule.onNodeWithTag(SettingsTestTags.SHOW_PREVIEWS).assertIsOn().performClick()

        assert(toggled == false)
    }

    @Test
    fun loadFailure_showsTheErrorAndRetries() {
        var retried = false
        setContent(
            state = SettingsUiState(settings = null, isLoading = false, errorMessage = "No connection."),
            onRetry = { retried = true },
        )

        composeRule.onNodeWithTag(SettingsTestTags.ERROR).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.RETRY).performClick()

        assert(retried)
    }

    @Test
    fun saveFailure_showsADismissibleBannerAboveTheGroups() {
        var dismissed = false
        setContent(
            state = SettingsUiState(
                settings = UserSettings(showPreviews = true),
                errorMessage = "Couldn't save that.",
            ),
            onDismissError = { dismissed = true },
        )

        composeRule.onNodeWithText("Couldn't save that.").assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.DISMISS_ERROR).performClick()

        assert(dismissed)
    }

    @Test
    fun loading_showsTheProgressIndicator() {
        setContent(SettingsUiState(settings = null, isLoading = true))

        composeRule.onNodeWithTag(SettingsTestTags.PROGRESS).assertIsDisplayed()
    }

    // --- Message preferences (issue #32) -------------------------------------

    @Test
    fun messageSettings_showsTheStoredBooleanPreferences() {
        setContent(
            SettingsUiState(
                settings = UserSettings(
                    defaultPubliclyVisible = false,
                    showAdvancedPostSettings = true,
                ),
            ),
        )

        composeRule.onNodeWithTag(SettingsTestTags.MESSAGE_SETTINGS).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.DEFAULT_PUBLICLY_VISIBLE).assertIsOff()
        composeRule.onNodeWithTag(SettingsTestTags.SHOW_ADVANCED_POST_SETTINGS).assertIsOn()
    }

    @Test
    fun togglingDefaultVisibility_reportsTheNewValue() {
        var toggled: Boolean? = null
        setContent(
            state = SettingsUiState(settings = UserSettings(defaultPubliclyVisible = true)),
            onToggleDefaultPubliclyVisible = { toggled = it },
        )

        composeRule.onNodeWithTag(SettingsTestTags.DEFAULT_PUBLICLY_VISIBLE).performClick()

        assert(toggled == false)
    }

    @Test
    fun togglingAdvancedPostSettings_reportsTheNewValue() {
        var toggled: Boolean? = null
        setContent(
            state = SettingsUiState(settings = UserSettings(showAdvancedPostSettings = false)),
            onToggleShowAdvancedPostSettings = { toggled = it },
        )

        composeRule.onNodeWithTag(SettingsTestTags.SHOW_ADVANCED_POST_SETTINGS).performClick()

        assert(toggled == true)
    }

    @Test
    fun characterLimit_showsTheStoredValueUnderProfile() {
        setContent(SettingsUiState(settings = UserSettings(maxMessageLength = 666)))

        composeRule.onNodeWithTag(SettingsTestTags.PROFILE).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.MAX_MESSAGE_LENGTH).assertTextEquals("666")
    }

    @Test
    fun characterLimit_fallsBackToTheServerDefaultWhenUnset() {
        setContent(SettingsUiState(settings = UserSettings(maxMessageLength = null)))

        composeRule.onNodeWithTag(SettingsTestTags.MAX_MESSAGE_LENGTH).assertTextEquals("666")
    }

    @Test
    fun steppingTheCharacterLimit_reportsTheSteppedValue() {
        var saved: Int? = null
        setContent(
            state = SettingsUiState(settings = UserSettings(maxMessageLength = 666)),
            onSetMaxMessageLength = { saved = it },
        )

        composeRule.onNodeWithTag(settingsIncrementTag(SettingsTestTags.MAX_MESSAGE_LENGTH))
            .performClick()

        assert(saved == 676) { "expected a 10-character step, got $saved" }
    }

    @Test
    fun messagesPerPage_stepsWithinTheDocumentedRange() {
        var saved: Int? = null
        setContent(
            state = SettingsUiState(settings = UserSettings(messagesPerPage = 20)),
            onSetMessagesPerPage = { saved = it },
        )

        composeRule.onNodeWithTag(settingsDecrementTag(SettingsTestTags.MESSAGES_PER_PAGE))
            .performClick()

        assert(saved == 19)
    }

    @Test
    fun messagesPerPage_cannotStepBelowTheMinimum() {
        var saved: Int? = null
        setContent(
            state = SettingsUiState(settings = UserSettings(messagesPerPage = 10)),
            onSetMessagesPerPage = { saved = it },
        )

        composeRule.onNodeWithTag(settingsDecrementTag(SettingsTestTags.MESSAGES_PER_PAGE))
            .assertIsNotEnabled()

        assert(saved == null)
    }

    @Test
    fun messagesPerPage_outOfRangeEntryIsRejectedWithoutReportingAValue() {
        var saved: Int? = null
        setContent(
            state = SettingsUiState(settings = UserSettings(messagesPerPage = 20)),
            onSetMessagesPerPage = { saved = it },
        )

        composeRule.onNodeWithTag(SettingsTestTags.MESSAGES_PER_PAGE).performTextClearance()
        composeRule.onNodeWithTag(SettingsTestTags.MESSAGES_PER_PAGE).performTextInput("99")
        composeRule.onNodeWithTag(SettingsTestTags.MESSAGES_PER_PAGE).performImeAction()

        composeRule.onNodeWithTag(settingsNumberErrorTag(SettingsTestTags.MESSAGES_PER_PAGE))
            .assertIsDisplayed()
        assert(saved == null) { "an out-of-range entry must not be saved, got $saved" }
    }

    @Test
    fun messagesPerPage_inRangeEntryIsReported() {
        var saved: Int? = null
        setContent(
            state = SettingsUiState(settings = UserSettings(messagesPerPage = 20)),
            onSetMessagesPerPage = { saved = it },
        )

        composeRule.onNodeWithTag(SettingsTestTags.MESSAGES_PER_PAGE).performTextClearance()
        composeRule.onNodeWithTag(SettingsTestTags.MESSAGES_PER_PAGE).performTextInput("25")
        composeRule.onNodeWithTag(SettingsTestTags.MESSAGES_PER_PAGE).performImeAction()

        assert(saved == 25)
    }

    @Test
    fun characterLimit_rolledBackSaveRestoresTheFieldToTheStoredValue() {
        // The screen is recomposed with the previous value after a rejected save;
        // the field must follow rather than keep showing a value that never saved.
        var settings by mutableStateOf(UserSettings(maxMessageLength = 666))
        composeRule.setContent {
            InterlinedListTheme {
                SettingsScreen(
                    state = SettingsUiState(settings = settings, errorMessage = null),
                    onBack = {},
                    onRetry = {},
                    onSelectViewingPreference = {},
                    onToggleShowPreviews = {},
                    onSetMessagesPerPage = {},
                    // Optimistic apply, then the server refuses and it rolls back.
                    onSetMaxMessageLength = { settings = settings.copy(maxMessageLength = it) },
                    onToggleDefaultPubliclyVisible = {},
                    onToggleShowAdvancedPostSettings = {},
                    onDismissError = {},
                )
            }
        }

        composeRule.onNodeWithTag(settingsIncrementTag(SettingsTestTags.MAX_MESSAGE_LENGTH))
            .performClick()
        composeRule.onNodeWithTag(SettingsTestTags.MAX_MESSAGE_LENGTH).assertTextEquals("676")

        settings = settings.copy(maxMessageLength = 666)

        composeRule.onNodeWithTag(SettingsTestTags.MAX_MESSAGE_LENGTH).assertTextEquals("666")
    }
}
