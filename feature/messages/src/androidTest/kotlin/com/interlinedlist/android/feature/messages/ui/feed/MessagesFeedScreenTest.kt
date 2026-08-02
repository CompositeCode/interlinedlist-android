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
import com.interlinedlist.android.feature.messages.ui.components.EditMessageSheetTags
import com.interlinedlist.android.feature.messages.ui.components.MessageCardTags
import com.interlinedlist.android.feature.messages.ui.components.MessageMediaTags
import com.interlinedlist.android.feature.messages.ui.components.ModerationDialogTags
import com.interlinedlist.android.feature.messages.ui.components.ReportDialogTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MessagesFeedScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun message(
        id: String,
        body: String,
        imageUrls: List<String> = emptyList(),
        mine: Boolean = false,
        editedAt: String? = null,
    ) = Message(
        id = id, content = body, authorId = "u1", authorUsername = "adron",
        authorDisplayName = "Adron", authorAvatarUrl = null, createdAt = null,
        digCount = 0, replyCount = 0, dugByMe = false, parentId = null, mine = mine,
        imageUrls = imageUrls, editedAt = editedAt,
    )

    /** Hosts the stateless feed with a tiny in-memory state holder. */
    private fun setFeed(
        initial: MessagesFeedUiState,
        onOpenMessage: (String) -> Unit = {},
        onReport: (Message) -> Unit = {},
        onEdit: (Message) -> Unit = {},
        onBlockUser: (Message) -> Unit = {},
        onMuteUser: (Message) -> Unit = {},
        onReportUser: (Message) -> Unit = {},
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
                    onReport = onReport,
                    onEdit = onEdit,
                    onBlockUser = onBlockUser,
                    onMuteUser = onMuteUser,
                    onReportUser = onReportUser,
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

    @Test
    fun attachedImage_isRendered() {
        setFeed(
            MessagesFeedUiState(
                messages = listOf(message("1", "with photo", imageUrls = listOf("https://cdn/a.png"))),
            ),
        )
        composeRule.onNodeWithTag(MessageMediaTags.IMAGE).assertIsDisplayed()
    }

    @Test
    fun overflowMenu_reportsAnotherUsersMessage() {
        var reported: String? = null
        setFeed(
            MessagesFeedUiState(messages = listOf(message("77", "not mine"))),
            onReport = { reported = it.id },
        )
        composeRule.onNodeWithTag(MessageCardTags.MENU).performClick()
        composeRule.onNodeWithTag(MessageCardTags.REPORT).performClick()
        assert(reported == "77")
    }

    @Test
    fun reportDialog_isShown_whenReportTargetIsSet() {
        setFeed(MessagesFeedUiState(reportTarget = message("77", "not mine")))
        composeRule.onNodeWithTag(ReportDialogTags.DIALOG).assertIsDisplayed()
    }

    @Test
    fun overflowMenu_offersEdit_onOwnMessage() {
        var edited: String? = null
        setFeed(
            MessagesFeedUiState(messages = listOf(message("mine1", "my post", mine = true))),
            onEdit = { edited = it.id },
        )
        composeRule.onNodeWithTag(MessageCardTags.MENU).performClick()
        composeRule.onNodeWithTag(MessageCardTags.EDIT).performClick()
        assert(edited == "mine1")
    }

    @Test
    fun overflowMenu_offersAuthorModeration_onOthersMessage() {
        var blocked: String? = null
        var muted: String? = null
        var reportedUser: String? = null
        setFeed(
            MessagesFeedUiState(messages = listOf(message("77", "not mine"))),
            onBlockUser = { blocked = it.id },
            onMuteUser = { muted = it.id },
            onReportUser = { reportedUser = it.id },
        )
        composeRule.onNodeWithTag(MessageCardTags.MENU).performClick()
        composeRule.onNodeWithTag(MessageCardTags.BLOCK_USER).assertIsDisplayed()
        composeRule.onNodeWithTag(MessageCardTags.MUTE_USER).assertIsDisplayed()
        composeRule.onNodeWithTag(MessageCardTags.REPORT_USER).performClick()
        assert(reportedUser == "77")
    }

    @Test
    fun editSheet_isShown_whenEditTargetIsSet() {
        setFeed(
            MessagesFeedUiState(
                editTarget = message("mine1", "my post", mine = true),
                editText = "my post",
            ),
        )
        composeRule.onNodeWithTag(EditMessageSheetTags.INPUT).assertIsDisplayed()
    }

    @Test
    fun moderationDialog_isShown_whenModerationTargetIsSet() {
        setFeed(
            MessagesFeedUiState(
                moderationTarget = com.interlinedlist.android.feature.messages.ui.feed.ModerationTarget(
                    message = message("77", "not mine"),
                    action = com.interlinedlist.android.feature.messages.ui.feed.ModerationAction.BLOCK,
                ),
            ),
        )
        composeRule.onNodeWithTag(ModerationDialogTags.DIALOG).assertIsDisplayed()
    }

    @Test
    fun editedMarker_isShown_forEditedMessage() {
        setFeed(
            MessagesFeedUiState(
                messages = listOf(message("1", "edited body", editedAt = "2026-07-31T12:00:00Z")),
            ),
        )
        composeRule.onNodeWithTag(MessageCardTags.EDITED).assertIsDisplayed()
    }

    @Test
    fun scheduledAction_isPresent_inTheTopBar() {
        setFeed(MessagesFeedUiState(messages = listOf(message("1", "hi"))))
        composeRule.onNodeWithTag(MessagesFeedTags.SCHEDULED_ACTION).assertIsDisplayed()
    }
}
