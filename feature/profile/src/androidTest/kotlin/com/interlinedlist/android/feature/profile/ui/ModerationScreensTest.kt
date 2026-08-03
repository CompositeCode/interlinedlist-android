package com.interlinedlist.android.feature.profile.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.core.model.CustomerStatus
import com.interlinedlist.android.feature.profile.domain.ModeratedUser
import com.interlinedlist.android.feature.profile.domain.ModerationStatus
import com.interlinedlist.android.feature.profile.domain.ProfileUser
import com.interlinedlist.android.feature.profile.ui.account.BlockedMutedScreen
import com.interlinedlist.android.feature.profile.ui.account.BlockedMutedTestTags
import com.interlinedlist.android.feature.profile.ui.account.BlockedMutedUiState
import com.interlinedlist.android.feature.profile.ui.profile.ProfileModerationTestTags
import com.interlinedlist.android.feature.profile.ui.profile.ProfileUiState
import com.interlinedlist.android.feature.profile.ui.profile.UserProfileScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ModerationScreensTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val blocked = listOf(ModeratedUser("b1", "spammer", "Spam Bot", null))
    private val muted = listOf(ModeratedUser("m1", "noisy", "Very Loud", null))

    @Test
    fun blockedMuted_rendersBothSections() {
        composeRule.setContent {
            InterlinedListTheme {
                BlockedMutedScreen(
                    state = BlockedMutedUiState(blocked = blocked, muted = muted, isLoading = false),
                    onUnblock = {},
                    onUnmute = {},
                    onBack = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithTag(BlockedMutedTestTags.LIST).assertIsDisplayed()
        composeRule.onNodeWithTag(BlockedMutedTestTags.blockedRow("spammer")).assertIsDisplayed()
        composeRule.onNodeWithTag(BlockedMutedTestTags.mutedRow("noisy")).assertIsDisplayed()
    }

    @Test
    fun blockedMuted_unblockInvokesCallback() {
        var unblocked: String? = null
        composeRule.setContent {
            InterlinedListTheme {
                BlockedMutedScreen(
                    state = BlockedMutedUiState(blocked = blocked, muted = muted, isLoading = false),
                    onUnblock = { unblocked = it },
                    onUnmute = {},
                    onBack = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithTag(BlockedMutedTestTags.unblock("spammer")).performClick()
        assert(unblocked == "spammer")
    }

    @Test
    fun profileOverflow_showsModerationActions() {
        composeRule.setContent {
            InterlinedListTheme {
                UserProfileScreen(
                    state = ProfileUiState(
                        user = ProfileUser(
                            id = "u2",
                            username = "ada",
                            displayName = "Ada Lovelace",
                            avatarUrl = null,
                            bio = null,
                            customerStatus = CustomerStatus.FREE,
                            isCurrentUser = false,
                        ),
                        isLoading = false,
                        moderationStatus = ModerationStatus(),
                    ),
                    onBack = {},
                    onRetry = {},
                )
            }
        }

        // The overflow menu is offered for another user; opening it reveals the actions.
        composeRule.onNodeWithTag(ProfileModerationTestTags.OVERFLOW).performClick()
        composeRule.onNodeWithTag(ProfileModerationTestTags.MENU_BLOCK).assertIsDisplayed()
        composeRule.onNodeWithTag(ProfileModerationTestTags.MENU_MUTE).assertIsDisplayed()
        composeRule.onNodeWithTag(ProfileModerationTestTags.MENU_REPORT).assertIsDisplayed()
    }
}
