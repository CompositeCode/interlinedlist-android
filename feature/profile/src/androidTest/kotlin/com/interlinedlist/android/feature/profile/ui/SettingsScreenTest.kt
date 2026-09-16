package com.interlinedlist.android.feature.profile.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.profile.domain.UserSettings
import com.interlinedlist.android.feature.profile.domain.ViewingPreference
import com.interlinedlist.android.feature.profile.ui.settings.SettingsScreen
import com.interlinedlist.android.feature.profile.ui.settings.SettingsTestTags
import com.interlinedlist.android.feature.profile.ui.settings.SettingsUiState
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
}
