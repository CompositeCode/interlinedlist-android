package com.interlinedlist.android.feature.profile.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.profile.domain.Coordinates
import com.interlinedlist.android.feature.profile.ui.settings.LocationNotice
import com.interlinedlist.android.feature.profile.ui.settings.LocationPermissionRationaleDialog
import com.interlinedlist.android.feature.profile.ui.settings.ProfileLocationGroup
import com.interlinedlist.android.feature.profile.ui.settings.ProfileLocationTestTags
import com.interlinedlist.android.feature.profile.ui.settings.ProfileLocationUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The profile-location UI (issue #37). The section is rendered on its own, without the
 * permission plumbing that wraps it, so these cover what the user sees and reports.
 */
@RunWith(AndroidJUnit4::class)
class ProfileLocationSectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(
        state: ProfileLocationUiState,
        onUseDeviceLocation: () -> Unit = {},
        onSaveLocation: (Double, Double) -> Unit = { _, _ -> },
        onDismissNotice: () -> Unit = {},
    ) {
        composeRule.setContent {
            InterlinedListTheme {
                ProfileLocationGroup(
                    state = state,
                    onUseDeviceLocation = onUseDeviceLocation,
                    onSaveLocation = onSaveLocation,
                    onDismissNotice = onDismissNotice,
                )
            }
        }
    }

    @Test
    fun storedLocation_isShownWithWhatHappensToIt() {
        setContent(ProfileLocationUiState(coordinates = Coordinates(47.6062, -122.3321)))

        composeRule.onNodeWithTag(ProfileLocationTestTags.GROUP).assertIsDisplayed()
        composeRule.onNodeWithTag(ProfileLocationTestTags.VALUE)
            .assertTextContains("Saved location: 47.6062, -122.3321")
        // The privacy note is not optional decoration: it says where this ends up.
        composeRule.onNodeWithTag(ProfileLocationTestTags.PRIVACY_NOTE).assertIsDisplayed()
    }

    @Test
    fun storedLocation_explainsThatItCannotBeRemoved() {
        // The API refuses every way of unsetting a location, so the button that would
        // do it must never look pressable — and the reason has to be on screen, at the
        // control, rather than left for the user to discover by failing.
        setContent(ProfileLocationUiState(coordinates = Coordinates(47.6062, -122.3321)))

        composeRule.onNodeWithTag(ProfileLocationTestTags.CLEAR).assertIsNotEnabled()
        composeRule.onNodeWithTag(ProfileLocationTestTags.CANNOT_REMOVE).assertIsDisplayed()
    }

    @Test
    fun storedLocation_canBeReplacedByTypingAnother() {
        // Replacing is the only supported way to change a saved location.
        var saved: Pair<Double, Double>? = null
        setContent(
            state = ProfileLocationUiState(coordinates = Coordinates(47.6062, -122.3321)),
            onSaveLocation = { latitude, longitude -> saved = latitude to longitude },
        )

        composeRule.onNodeWithTag(ProfileLocationTestTags.LATITUDE).performTextClearance()
        composeRule.onNodeWithTag(ProfileLocationTestTags.LATITUDE).performTextInput("45.52")
        composeRule.onNodeWithTag(ProfileLocationTestTags.LONGITUDE).performTextClearance()
        composeRule.onNodeWithTag(ProfileLocationTestTags.LONGITUDE).performTextInput("-122.68")
        composeRule.onNodeWithTag(ProfileLocationTestTags.SAVE).performClick()

        assertThat(saved).isEqualTo(45.52 to -122.68)
    }

    @Test
    fun noStoredLocation_saysSoAndOffersNothingToClear() {
        setContent(ProfileLocationUiState(coordinates = null))

        composeRule.onNodeWithTag(ProfileLocationTestTags.VALUE)
            .assertTextContains("No location saved.")
        composeRule.onNodeWithTag(ProfileLocationTestTags.CLEAR).assertIsNotEnabled()
        composeRule.onNodeWithTag(ProfileLocationTestTags.SAVE).assertIsNotEnabled()
        // Nothing to remove, so nothing to explain about removal.
        composeRule.onNodeWithTag(ProfileLocationTestTags.CANNOT_REMOVE).assertDoesNotExist()
    }

    @Test
    fun typingCoordinates_andSaving_reportsThem() {
        var saved: Pair<Double, Double>? = null
        setContent(
            state = ProfileLocationUiState(coordinates = null),
            onSaveLocation = { latitude, longitude -> saved = latitude to longitude },
        )

        composeRule.onNodeWithTag(ProfileLocationTestTags.LATITUDE).performTextInput("47.6062")
        composeRule.onNodeWithTag(ProfileLocationTestTags.LONGITUDE).performTextInput("-122.3321")
        composeRule.onNodeWithTag(ProfileLocationTestTags.SAVE).performClick()

        assertThat(saved).isEqualTo(47.6062 to -122.3321)
    }

    @Test
    fun outOfRangeEntry_cannotBeSavedAndSaysWhy() {
        var saved: Pair<Double, Double>? = null
        setContent(
            state = ProfileLocationUiState(coordinates = null),
            onSaveLocation = { latitude, longitude -> saved = latitude to longitude },
        )

        composeRule.onNodeWithTag(ProfileLocationTestTags.LATITUDE).performTextInput("91")
        composeRule.onNodeWithTag(ProfileLocationTestTags.LONGITUDE).performTextInput("0")

        composeRule.onNodeWithTag(ProfileLocationTestTags.ENTRY_ERROR).assertIsDisplayed()
        composeRule.onNodeWithTag(ProfileLocationTestTags.SAVE).assertIsNotEnabled()
        composeRule.onNodeWithTag(ProfileLocationTestTags.SAVE).performClick()
        assertThat(saved).isNull()
    }

    @Test
    fun editingBackIntoRange_reEnablesSaving() {
        setContent(ProfileLocationUiState(coordinates = null))

        composeRule.onNodeWithTag(ProfileLocationTestTags.LATITUDE).performTextInput("991")
        composeRule.onNodeWithTag(ProfileLocationTestTags.LONGITUDE).performTextInput("10")
        composeRule.onNodeWithTag(ProfileLocationTestTags.SAVE).assertIsNotEnabled()

        composeRule.onNodeWithTag(ProfileLocationTestTags.LATITUDE).performTextClearance()
        composeRule.onNodeWithTag(ProfileLocationTestTags.LATITUDE).performTextInput("47.6")

        composeRule.onNodeWithTag(ProfileLocationTestTags.SAVE).assertIsEnabled()
    }

    @Test
    fun useMyLocation_reportsTheRequest() {
        var asked = false
        setContent(
            state = ProfileLocationUiState(coordinates = null),
            onUseDeviceLocation = { asked = true },
        )

        composeRule.onNodeWithTag(ProfileLocationTestTags.USE_DEVICE).performClick()

        assertThat(asked).isTrue()
    }

    @Test
    fun whileReadingTheDevice_theButtonIsBusy() {
        setContent(ProfileLocationUiState(coordinates = null, isReadingDevice = true))

        composeRule.onNodeWithTag(ProfileLocationTestTags.USE_DEVICE).assertIsNotEnabled()
    }

    @Test
    fun aRefusedPermission_isShownAndTheFieldsStayUsable() {
        var saved: Pair<Double, Double>? = null
        setContent(
            state = ProfileLocationUiState(
                coordinates = null,
                notice = LocationNotice("Location permission wasn't granted."),
            ),
            onSaveLocation = { latitude, longitude -> saved = latitude to longitude },
        )

        composeRule.onNodeWithTag(ProfileLocationTestTags.NOTICE).assertIsDisplayed()
        // Manual entry is the whole point of the fallback, so it must still work.
        composeRule.onNodeWithTag(ProfileLocationTestTags.LATITUDE).performTextInput("47.6")
        composeRule.onNodeWithTag(ProfileLocationTestTags.LONGITUDE).performTextInput("-122.3")
        composeRule.onNodeWithTag(ProfileLocationTestTags.SAVE).performClick()

        assertThat(saved).isEqualTo(47.6 to -122.3)
    }

    @Test
    fun rationale_explainsBeforeAsking() {
        var continued = false
        var dismissed = false
        composeRule.setContent {
            InterlinedListTheme {
                LocationPermissionRationaleDialog(
                    onContinue = { continued = true },
                    onDismiss = { dismissed = true },
                )
            }
        }

        composeRule.onNodeWithTag(ProfileLocationTestTags.RATIONALE).assertIsDisplayed()
        composeRule.onNodeWithTag(ProfileLocationTestTags.RATIONALE_DISMISS).performClick()
        assertThat(dismissed).isTrue()
        assertThat(continued).isFalse()

        composeRule.onNodeWithTag(ProfileLocationTestTags.RATIONALE_CONTINUE).performClick()
        assertThat(continued).isTrue()
    }
}
