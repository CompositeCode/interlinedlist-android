package com.interlinedlist.android.feature.directmessages.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.directmessages.ui.model.MessageBubble
import com.interlinedlist.android.feature.directmessages.ui.thread.ThreadScreen
import com.interlinedlist.android.feature.directmessages.ui.thread.ThreadTestTags
import com.interlinedlist.android.feature.directmessages.ui.thread.ThreadUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThreadScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun bubble(id: String, mine: Boolean, body: String = "hello") = MessageBubble(
        id = id, body = body, imageUrls = emptyList(), isMine = mine,
        isRead = false, isPending = false, createdAtMillis = 1L,
    )

    @Test
    fun rendersMineAndTheirBubbles() {
        composeRule.setContent {
            InterlinedListTheme {
                ThreadScreen(
                    state = ThreadUiState(
                        username = "adron",
                        messages = listOf(
                            bubble("m1", mine = false, body = "hi from them"),
                            bubble("m2", mine = true, body = "hi from me"),
                        ),
                        isLoading = false,
                    ),
                    onBack = {},
                    onDraftChange = {},
                    onSend = {},
                    onAttachImage = {},
                )
            }
        }

        composeRule.onNodeWithTag(ThreadTestTags.bubble("m1")).assertIsDisplayed()
        composeRule.onNodeWithTag(ThreadTestTags.bubble("m2")).assertIsDisplayed()
        // The read-receipt label appears on the user's own message.
        composeRule.onNodeWithTag(ThreadTestTags.READ_RECEIPT).assertIsDisplayed()
    }

    @Test
    fun composer_enablesSend_whenDraftNotBlank_andSends() {
        var sent = false
        composeRule.setContent {
            var state by mutableStateOf(
                ThreadUiState(username = "adron", messages = emptyList(), isLoading = false),
            )
            InterlinedListTheme {
                ThreadScreen(
                    state = state,
                    onBack = {},
                    onDraftChange = { state = state.copy(draft = it) },
                    onSend = { sent = true },
                    onAttachImage = {},
                )
            }
        }

        composeRule.onNodeWithTag(ThreadTestTags.SEND).assertIsNotEnabled()
        composeRule.onNodeWithTag(ThreadTestTags.COMPOSER).performTextInput("hey")
        composeRule.onNodeWithTag(ThreadTestTags.SEND).assertIsEnabled()
        composeRule.onNodeWithTag(ThreadTestTags.SEND).performClick()
        assert(sent)
    }

    @Test
    fun emptyState_isShown_whenNoMessages() {
        composeRule.setContent {
            InterlinedListTheme {
                ThreadScreen(
                    state = ThreadUiState(username = "adron", messages = emptyList(), isLoading = false),
                    onBack = {},
                    onDraftChange = {},
                    onSend = {},
                    onAttachImage = {},
                )
            }
        }

        composeRule.onNodeWithTag(ThreadTestTags.EMPTY).assertIsDisplayed()
    }
}
