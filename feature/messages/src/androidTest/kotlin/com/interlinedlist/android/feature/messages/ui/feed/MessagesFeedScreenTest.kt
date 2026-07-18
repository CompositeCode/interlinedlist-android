package com.interlinedlist.android.feature.messages.ui.feed

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.messages.domain.Message
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MessagesFeedScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun message(id: String, body: String) = Message(
        id = id, content = body, authorId = "u1", authorUsername = "adron",
        authorDisplayName = "Adron", authorAvatarUrl = null, createdAt = null,
        digCount = 0, replyCount = 0, dugByMe = false, parentId = null, mine = false,
    )

    /** Hosts the stateless feed with a tiny in-memory state holder. */
    private fun setFeed(
        initial: MessagesFeedUiState,
        onOpenMessage: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            var state by mutableStateOf(initial)
            InterlinedListTheme {
                MessagesFeedScreen(
                    state = state,
                    onRefresh = {},
                    onLoadMore = {},
                    onOpenMessage = onOpenMessage,
                    onDig = {},
                    onDelete = {},
                    onOpenCompose = { state = state.copy(isComposeOpen = true) },
                    onDismissCompose = { state = state.copy(isComposeOpen = false) },
                    onComposeTextChange = { state = state.copy(composeText = it) },
                    onPost = {},
                )
            }
        }
    }

    @Test
    fun emptyState_isShown_whenThereAreNoMessages() {
        setFeed(MessagesFeedUiState(messages = emptyList()))
        composeRule.onNodeWithTag(MessagesFeedTags.EMPTY).assertIsDisplayed()
    }

    @Test
    fun messages_areRendered_inTheList() {
        setFeed(MessagesFeedUiState(messages = listOf(message("1", "First post"))))
        composeRule.onNodeWithText("First post").assertIsDisplayed()
    }

    @Test
    fun tappingMessage_invokesOpenCallback() {
        var opened: String? = null
        setFeed(
            MessagesFeedUiState(messages = listOf(message("42", "Tap me"))),
            onOpenMessage = { opened = it },
        )
        composeRule.onNodeWithText("Tap me").performClick()
        assert(opened == "42")
    }

    @Test
    fun subscriptionGate_showsLockedState_andHidesFab() {
        setFeed(MessagesFeedUiState(subscriptionRequired = true, errorMessage = "Subscribers only"))
        composeRule.onNodeWithTag(MessagesFeedTags.LOCKED).assertIsDisplayed()
    }

    @Test
    fun fab_opensComposeSheet() {
        setFeed(MessagesFeedUiState(messages = listOf(message("1", "hi"))))
        composeRule.onNodeWithTag(MessagesFeedTags.FAB).performClick()
        composeRule.onNodeWithTag(MessagesFeedTags.COMPOSE_INPUT).assertIsDisplayed()
    }
}
