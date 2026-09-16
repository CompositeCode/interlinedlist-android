package com.interlinedlist.android.feature.directmessages.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.directmessages.data.Recipient
import com.interlinedlist.android.feature.directmessages.ui.newmessage.NewMessageScreen
import com.interlinedlist.android.feature.directmessages.ui.newmessage.NewMessageTestTags
import com.interlinedlist.android.feature.directmessages.ui.newmessage.NewMessageUiState
import com.interlinedlist.android.feature.directmessages.ui.newmessage.RecipientRuleCopy
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The recipient picker must tell three stories apart: still loading, loaded but
 * nobody is messageable yet (the mutual-follow rule), and an actual failure.
 */
@RunWith(AndroidJUnit4::class)
class NewMessageScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val adron = Recipient("u1", "adron", "Adron Hall", null)

    private fun setScreen(
        state: NewMessageUiState,
        onFindPeople: () -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        composeRule.setContent {
            InterlinedListTheme {
                NewMessageScreen(
                    state = state,
                    onQueryChange = {},
                    onBack = {},
                    onRecipientChosen = {},
                    onFindPeople = onFindPeople,
                    onRetry = onRetry,
                )
            }
        }
    }

    @Test
    fun emptyRecipients_explainsTheRule_andOffersAWayOut() {
        setScreen(NewMessageUiState(recipients = emptyList(), isLoading = false))

        composeRule.onNodeWithTag(NewMessageTestTags.EMPTY).assertIsDisplayed()
        composeRule.onNodeWithText(RecipientRuleCopy.TITLE).assertIsDisplayed()
        composeRule.onNodeWithText(RecipientRuleCopy.EXPLANATION).assertIsDisplayed()
        composeRule.onNodeWithTag(NewMessageTestTags.FIND_PEOPLE).assertIsDisplayed()
        composeRule.onNodeWithTag(NewMessageTestTags.LIST).assertDoesNotExist()
        composeRule.onNodeWithTag(NewMessageTestTags.ERROR).assertDoesNotExist()
    }

    @Test
    fun findPeople_routesToUserSearch() {
        var tapped = false
        setScreen(
            state = NewMessageUiState(recipients = emptyList(), isLoading = false),
            onFindPeople = { tapped = true },
        )

        composeRule.onNodeWithTag(NewMessageTestTags.FIND_PEOPLE).performClick()

        assertThat(tapped).isTrue()
    }

    @Test
    fun recipients_areStillListed() {
        setScreen(NewMessageUiState(recipients = listOf(adron), isLoading = false))

        composeRule.onNodeWithTag(NewMessageTestTags.LIST).assertIsDisplayed()
        composeRule.onNodeWithTag(NewMessageTestTags.row("adron")).assertIsDisplayed()
        composeRule.onNodeWithTag(NewMessageTestTags.EMPTY).assertDoesNotExist()
    }

    @Test
    fun loading_showsProgress_notTheEmptyExplanation() {
        setScreen(NewMessageUiState(isLoading = true))

        composeRule.onNodeWithTag(NewMessageTestTags.PROGRESS).assertIsDisplayed()
        composeRule.onNodeWithTag(NewMessageTestTags.EMPTY).assertDoesNotExist()
    }

    @Test
    fun error_showsRetry_notTheEmptyExplanation() {
        var retried = false
        setScreen(
            state = NewMessageUiState(isLoading = false, errorMessage = "No connection."),
            onRetry = { retried = true },
        )

        composeRule.onNodeWithTag(NewMessageTestTags.ERROR).assertIsDisplayed()
        composeRule.onNodeWithText(RecipientRuleCopy.TITLE).assertDoesNotExist()
        composeRule.onNodeWithTag(NewMessageTestTags.EMPTY).assertDoesNotExist()

        composeRule.onNodeWithTag(NewMessageTestTags.RETRY).performClick()

        assertThat(retried).isTrue()
    }
}
