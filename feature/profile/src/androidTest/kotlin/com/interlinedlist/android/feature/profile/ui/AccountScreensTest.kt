package com.interlinedlist.android.feature.profile.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.profile.domain.LoginSession
import com.interlinedlist.android.feature.profile.ui.account.SessionsScreen
import com.interlinedlist.android.feature.profile.ui.account.SessionsTestTags
import com.interlinedlist.android.feature.profile.ui.account.SessionsUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountScreensTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sessions = listOf(
        LoginSession("s1", "Pixel 8", null, "2026-07-31T21:49:00.000Z", isCurrent = true),
        LoginSession("s2", "Chrome on macOS", null, "2026-07-30T09:00:00.000Z", isCurrent = false),
    )

    @Test
    fun sessions_renderRowsAndCurrentDeviceBadge() {
        composeRule.setContent {
            InterlinedListTheme {
                SessionsScreen(
                    state = SessionsUiState(sessions = sessions, isLoading = false),
                    onRevoke = {},
                    onBack = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithTag(SessionsTestTags.LIST).assertIsDisplayed()
        composeRule.onNodeWithTag(SessionsTestTags.row("s1")).assertIsDisplayed()
        composeRule.onNodeWithTag(SessionsTestTags.row("s2")).assertIsDisplayed()
        // The current device shows a "This device" badge...
        composeRule.onNodeWithTag(SessionsTestTags.CURRENT_BADGE).assertIsDisplayed()
    }

    @Test
    fun sessions_revokeShowsConfirmDialogThenInvokesCallback() {
        var revoked: String? = null
        composeRule.setContent {
            InterlinedListTheme {
                SessionsScreen(
                    state = SessionsUiState(sessions = sessions, isLoading = false),
                    onRevoke = { revoked = it },
                    onBack = {},
                    onRetry = {},
                )
            }
        }

        // Tapping "Sign out" on a non-current session opens the confirm dialog.
        composeRule.onNodeWithTag(SessionsTestTags.revoke("s2")).performClick()
        composeRule.onNodeWithTag(SessionsTestTags.CONFIRM_DIALOG).assertIsDisplayed()

        // Confirming revokes that session.
        composeRule.onNodeWithTag(SessionsTestTags.CONFIRM_REVOKE).performClick()
        assert(revoked == "s2")
    }
}
