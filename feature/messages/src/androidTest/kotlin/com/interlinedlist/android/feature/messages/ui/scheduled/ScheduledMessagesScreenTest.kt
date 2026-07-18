package com.interlinedlist.android.feature.messages.ui.scheduled

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
class ScheduledMessagesScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun scheduled(id: String, body: String) = Message(
        id = id, content = body, authorId = "u1", authorUsername = "adron",
        authorDisplayName = "Adron", authorAvatarUrl = null, createdAt = null,
        digCount = 0, replyCount = 0, dugByMe = false, parentId = null, mine = true,
        scheduledAt = "2026-07-19T09:00:00Z",
    )

    private fun setScreen(
        state: ScheduledUiState,
        onCancel: (Message) -> Unit = {},
    ) {
        composeRule.setContent {
            InterlinedListTheme {
                ScheduledMessagesScreen(
                    state = state,
                    onBack = {},
                    onRefresh = {},
                    onCancel = onCancel,
                )
            }
        }
    }

    @Test
    fun emptyState_isShown_whenThereAreNoScheduledMessages() {
        setScreen(ScheduledUiState(messages = emptyList()))
        composeRule.onNodeWithTag(ScheduledMessagesTags.EMPTY).assertIsDisplayed()
    }

    @Test
    fun scheduledMessages_areRendered() {
        setScreen(ScheduledUiState(messages = listOf(scheduled("1", "Goes out tomorrow"))))
        composeRule.onNodeWithText("Goes out tomorrow").assertIsDisplayed()
    }

    @Test
    fun cancel_invokesCallbackWithMessage() {
        var cancelled: String? = null
        setScreen(
            ScheduledUiState(messages = listOf(scheduled("42", "Cancel me"))),
            onCancel = { cancelled = it.id },
        )
        composeRule.onNodeWithTag(ScheduledMessagesTags.CANCEL).performClick()
        assert(cancelled == "42")
    }

    @Test
    fun lockedState_isShown_whenSubscriptionRequired() {
        setScreen(ScheduledUiState(subscriptionRequired = true, errorMessage = "Subscribers only"))
        composeRule.onNodeWithTag(ScheduledMessagesTags.LOCKED).assertIsDisplayed()
    }
}
