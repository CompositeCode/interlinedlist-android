package com.interlinedlist.android.feature.auth.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.auth.nav.EmailChangeAction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The confirm/undo result screen. The undo half is a security affordance, so the
 * copy has to say plainly that the previous address was restored — these assertions
 * guard that wording against being softened into a generic "done".
 */
@RunWith(AndroidJUnit4::class)
class EmailChangeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setScreen(state: EmailChangeUiState, onDone: () -> Unit = {}) {
        composeRule.setContent {
            InterlinedListTheme { EmailChangeScreen(state = state, onDone = onDone) }
        }
    }

    @Test
    fun verifySuccess_saysTheAccountNowUsesTheNewAddress() {
        setScreen(EmailChangeUiState(EmailChangeAction.VERIFY, EmailChangeStatus.DONE))

        composeRule.onNodeWithTag(EmailChangeTestTags.HEADING).assertIsDisplayed()
        composeRule.onNodeWithTag(EmailChangeTestTags.BODY).assertIsDisplayed()
        assertThat(composeRule.textOf(EmailChangeTestTags.HEADING)).contains("updated")
    }

    @Test
    fun undoSuccess_spellsOutWhatWasRestoredAndWhatToDoNext() {
        setScreen(EmailChangeUiState(EmailChangeAction.UNDO, EmailChangeStatus.DONE))

        assertThat(composeRule.textOf(EmailChangeTestTags.HEADING)).contains("undone")
        val body = composeRule.textOf(EmailChangeTestTags.BODY)
        assertThat(body).contains("restored")
        assertThat(body).contains("change your password")
    }

    @Test
    fun failure_showsTheServersOwnMessage() {
        setScreen(
            EmailChangeUiState(
                action = EmailChangeAction.VERIFY,
                status = EmailChangeStatus.FAILED,
                message = "That email is already in use",
            ),
        )

        assertThat(composeRule.textOf(EmailChangeTestTags.BODY)).isEqualTo("That email is already in use")
    }

    @Test
    fun invalidLink_saysNothingChangedAndOffersNoRetry() {
        setScreen(EmailChangeUiState(EmailChangeAction.UNDO, EmailChangeStatus.INVALID_LINK))

        assertThat(composeRule.textOf(EmailChangeTestTags.BODY)).contains("nothing was changed")
        composeRule.onNodeWithTag(EmailChangeTestTags.PROGRESS).assertDoesNotExist()
    }

    @Test
    fun working_showsProgressAndNoDoneButton() {
        setScreen(EmailChangeUiState(EmailChangeAction.VERIFY, EmailChangeStatus.WORKING))

        composeRule.onNodeWithTag(EmailChangeTestTags.PROGRESS).assertIsDisplayed()
        composeRule.onNodeWithTag(EmailChangeTestTags.DONE).assertDoesNotExist()
    }

    @Test
    fun done_invokesTheCallback() {
        var done = 0
        setScreen(EmailChangeUiState(EmailChangeAction.UNDO, EmailChangeStatus.DONE)) { done++ }

        composeRule.onNodeWithTag(EmailChangeTestTags.DONE).performClick()

        assertThat(done).isEqualTo(1)
    }
}

/** Reads the text semantics of the node tagged [tag]. */
private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.textOf(tag: String): String =
    onNodeWithTag(tag)
        .fetchSemanticsNode()
        .config[androidx.compose.ui.semantics.SemanticsProperties.Text]
        .joinToString(separator = "") { it.text }
