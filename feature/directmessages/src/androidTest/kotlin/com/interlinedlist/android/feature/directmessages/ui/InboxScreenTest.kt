package com.interlinedlist.android.feature.directmessages.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.directmessages.data.Conversation
import com.interlinedlist.android.feature.directmessages.ui.inbox.InboxScreen
import com.interlinedlist.android.feature.directmessages.ui.inbox.InboxTestTags
import com.interlinedlist.android.feature.directmessages.ui.inbox.InboxUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InboxScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun conversation(username: String, unread: Boolean) = Conversation(
        username = username, displayName = "Adron Hall", avatarUrl = null,
        lastMessageBody = "hey there", lastMessageAtMillis = 1L, hasUnread = unread,
    )

    @Test
    fun emptyState_isShown_whenNoConversations() {
        composeRule.setContent {
            InterlinedListTheme {
                InboxScreen(
                    state = InboxUiState(conversations = emptyList()),
                    onRefresh = {},
                    onOpenThread = {},
                    onComposeNew = {},
                )
            }
        }

        composeRule.onNodeWithTag(InboxTestTags.EMPTY).assertIsDisplayed()
    }

    @Test
    fun conversationRow_showsUnreadDot_andOpensThreadOnClick() {
        var opened: String? = null
        composeRule.setContent {
            InterlinedListTheme {
                InboxScreen(
                    state = InboxUiState(
                        conversations = listOf(conversation("adron", unread = true)),
                    ),
                    onRefresh = {},
                    onOpenThread = { opened = it },
                    onComposeNew = {},
                )
            }
        }

        composeRule.onNodeWithTag(InboxTestTags.UNREAD_DOT).assertIsDisplayed()
        composeRule.onNodeWithTag(InboxTestTags.row("adron")).performClick()
        assert(opened == "adron")
    }

    @Test
    fun composeFab_invokesCallback() {
        var composed = false
        composeRule.setContent {
            InterlinedListTheme {
                InboxScreen(
                    state = InboxUiState(conversations = listOf(conversation("adron", false))),
                    onRefresh = {},
                    onOpenThread = {},
                    onComposeNew = { composed = true },
                )
            }
        }

        composeRule.onNodeWithTag(InboxTestTags.COMPOSE_FAB).performClick()
        assert(composed)
    }
}
