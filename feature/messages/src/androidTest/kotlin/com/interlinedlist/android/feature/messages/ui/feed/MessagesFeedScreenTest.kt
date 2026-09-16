package com.interlinedlist.android.feature.messages.ui.feed

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.core.model.ViewingPreference
import com.interlinedlist.android.feature.messages.domain.LinkedNetwork
import com.interlinedlist.android.feature.messages.domain.Message
import com.interlinedlist.android.feature.messages.domain.MessageVisibility
import com.interlinedlist.android.feature.messages.domain.PushedMessage
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
        publiclyVisible: Boolean = true,
        pushCount: Int = 0,
        pushedMessage: PushedMessage? = null,
    ) = Message(
        id = id, content = body, authorId = "u1", authorUsername = "adron",
        authorDisplayName = "Adron", authorAvatarUrl = null, createdAt = null,
        digCount = 0, replyCount = 0, dugByMe = false, parentId = null, mine = mine,
        imageUrls = imageUrls, editedAt = editedAt, publiclyVisible = publiclyVisible,
        pushCount = pushCount,
        pushedMessageId = pushedMessage?.id,
        pushedMessage = pushedMessage,
    )

    private fun original(
        id: String = "orig",
        body: String = "the original post",
    ) = PushedMessage(
        id = id, content = body, authorUsername = "quinn", authorDisplayName = "Quinn",
        authorAvatarUrl = null, createdAt = null,
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
        onViewingPreferenceChange: ((ViewingPreference) -> Unit)? = null,
        onPush: (Message) -> Unit = {},
        onQuote: (Message) -> Unit = {},
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
                    onToggleNetwork = { id ->
                        val selected = if (id in state.selectedNetworkIds) {
                            state.selectedNetworkIds - id
                        } else {
                            state.selectedNetworkIds + id
                        }
                        state = state.copy(selectedNetworkIds = selected)
                    },
                    onVisibilityChange = { state = state.copy(composeVisibility = it) },
                    onViewingPreferenceChange = { preference ->
                        onViewingPreferenceChange?.invoke(preference)
                            ?: run { state = state.copy(viewingPreference = preference) }
                    },
                    onReport = onReport,
                    onEdit = onEdit,
                    onBlockUser = onBlockUser,
                    onMuteUser = onMuteUser,
                    onReportUser = onReportUser,
                    onPush = onPush,
                    onQuote = { quoted ->
                        state = state.copy(isComposeOpen = true, quoteTarget = quoted)
                        onQuote(quoted)
                    },
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

    @Test
    fun destinationsRow_rendersInterlinedListAndLinkedNetwork_andTogglesIt() {
        val linkedIn = LinkedNetwork(id = "l1", provider = "linkedin", providerUsername = "Adron Hall")
        setFeed(
            MessagesFeedUiState(
                isComposeOpen = true,
                composeText = "cross-post me",
                linkedNetworks = listOf(linkedIn),
            ),
        )

        // InterlinedList is always present; the linked network chip is offered too.
        composeRule.onNodeWithTag(MessagesFeedTags.DESTINATION_IL).assertIsDisplayed()
        composeRule.onNodeWithTag(MessagesFeedTags.destinationTag("l1")).assertIsDisplayed()

        // Tapping the LinkedIn chip selects it as a cross-post target.
        composeRule.onNodeWithTag(MessagesFeedTags.destinationTag("l1")).performClick()
        composeRule.onNodeWithTag(MessagesFeedTags.destinationTag("l1")).assertIsSelected()
    }

    @Test
    fun destinationsHint_isShown_whenNoNetworksAreLinked() {
        setFeed(
            MessagesFeedUiState(
                isComposeOpen = true,
                composeText = "hi",
                linkedNetworks = emptyList(),
            ),
        )
        composeRule.onNodeWithTag(MessagesFeedTags.DESTINATIONS_HINT).assertIsDisplayed()
    }

    @Test
    fun privateMarker_isShown_onOwnPrivateMessage() {
        setFeed(
            MessagesFeedUiState(
                messages = listOf(message("1", "only me", mine = true, publiclyVisible = false)),
            ),
        )
        composeRule.onNodeWithTag(MessageCardTags.PRIVATE).assertIsDisplayed()
        composeRule.onNodeWithText("Private").assertIsDisplayed()
    }

    @Test
    fun privateMarker_isAbsent_onPublicMessage() {
        setFeed(
            MessagesFeedUiState(
                messages = listOf(message("1", "for everyone", mine = true, publiclyVisible = true)),
            ),
        )
        composeRule.onNodeWithTag(MessageCardTags.PRIVATE).assertDoesNotExist()
    }

    @Test
    fun visibilityChips_areShown_inComposer_andReflectTheDefault() {
        setFeed(
            MessagesFeedUiState(
                isComposeOpen = true,
                composeText = "hi",
                composeVisibility = MessageVisibility.PRIVATE,
            ),
        )

        composeRule.onNodeWithTag(MessagesFeedTags.VISIBILITY_PUBLIC).assertIsDisplayed()
        composeRule.onNodeWithTag(MessagesFeedTags.VISIBILITY_PRIVATE).assertIsSelected()
    }

    @Test
    fun visibilityChips_overrideTheDefault_whenTapped() {
        setFeed(
            MessagesFeedUiState(
                isComposeOpen = true,
                composeText = "hi",
                composeVisibility = MessageVisibility.PUBLIC,
            ),
        )

        composeRule.onNodeWithTag(MessagesFeedTags.VISIBILITY_PRIVATE).performClick()
        composeRule.onNodeWithTag(MessagesFeedTags.VISIBILITY_PRIVATE).assertIsSelected()
        composeRule.onNodeWithTag(MessagesFeedTags.VISIBILITY_HINT).assertIsDisplayed()
    }

    @Test
    fun viewPreferenceSwitcher_isShown_andReflectsTheSavedPreference() {
        setFeed(
            MessagesFeedUiState(
                messages = listOf(message("1", "hello")),
                viewingPreference = ViewingPreference.FOLLOWING,
            ),
        )

        composeRule.onNodeWithTag(MessagesFeedTags.VIEW_PREFERENCES).assertIsDisplayed()
        composeRule
            .onNodeWithTag(MessagesFeedTags.viewPreferenceTag(ViewingPreference.FOLLOWING))
            .assertIsSelected()
        composeRule.onNodeWithText("All Messages").assertIsDisplayed()
    }

    @Test
    fun viewPreferenceSwitcher_reportsTheTappedPreference() {
        val tapped = mutableListOf<ViewingPreference>()
        setFeed(
            MessagesFeedUiState(messages = listOf(message("1", "hello"))),
            onViewingPreferenceChange = { tapped += it },
        )

        composeRule
            .onNodeWithTag(MessagesFeedTags.viewPreferenceTag(ViewingPreference.MINE))
            .performClick()

        assertThat(tapped).containsExactly(ViewingPreference.MINE)
    }

    @Test
    fun viewPreferenceSwitcher_isDisabled_whileTheChoiceIsBeingSaved() {
        val tapped = mutableListOf<ViewingPreference>()
        setFeed(
            MessagesFeedUiState(
                messages = listOf(message("1", "hello")),
                isChangingViewingPreference = true,
            ),
            onViewingPreferenceChange = { tapped += it },
        )

        composeRule
            .onNodeWithTag(MessagesFeedTags.viewPreferenceTag(ViewingPreference.FOLLOWERS))
            .performClick()

        // No second request while one is in flight.
        assertThat(tapped).isEmpty()
    }

    @Test
    fun viewPreferenceSwitcher_staysAvailable_whenTheFeedIsGated() {
        setFeed(
            MessagesFeedUiState(
                subscriptionRequired = true,
                errorMessage = "Subscribers only",
            ),
        )

        // The user must still be able to switch away from a view they cannot see.
        composeRule.onNodeWithTag(MessagesFeedTags.VIEW_PREFERENCES).assertIsDisplayed()
        composeRule.onNodeWithTag(MessagesFeedTags.LOCKED).assertIsDisplayed()
    }

    // --- push / quote ------------------------------------------------------

    @Test
    fun push_rendersTheEmbeddedOriginal_insteadOfAnEmptyBody() {
        setFeed(
            MessagesFeedUiState(messages = listOf(message("p1", "", pushedMessage = original()))),
        )

        composeRule.onNodeWithTag(MessageCardTags.PUSH_HEADER).assertIsDisplayed()
        composeRule.onNodeWithText("Adron pushed").assertIsDisplayed()
        composeRule.onNodeWithTag(MessageCardTags.PUSHED_ORIGINAL).assertIsDisplayed()
        composeRule.onNodeWithText("the original post").assertIsDisplayed()
        composeRule.onNodeWithText("Quinn").assertIsDisplayed()
    }

    @Test
    fun quote_rendersTheOwnNote_andTheEmbeddedOriginal() {
        setFeed(
            MessagesFeedUiState(
                messages = listOf(message("q1", "worth reading", pushedMessage = original())),
            ),
        )

        // A quote has words of its own, so it carries no "pushed" header.
        composeRule.onNodeWithTag(MessageCardTags.PUSH_HEADER).assertDoesNotExist()
        composeRule.onNodeWithText("worth reading").assertIsDisplayed()
        composeRule.onNodeWithTag(MessageCardTags.PUSHED_ORIGINAL).assertIsDisplayed()
        composeRule.onNodeWithText("the original post").assertIsDisplayed()
    }

    @Test
    fun tappingTheEmbeddedOriginal_opensTheOriginalMessage() {
        var opened: String? = null
        setFeed(
            MessagesFeedUiState(messages = listOf(message("p1", "", pushedMessage = original()))),
            onOpenMessage = { opened = it },
        )

        composeRule.onNodeWithTag(MessageCardTags.PUSHED_ORIGINAL).performClick()

        assertThat(opened).isEqualTo("orig")
    }

    @Test
    fun pushAndQuote_areOffered_onSomeoneElsesPublicMessage() {
        val pushed = mutableListOf<Message>()
        setFeed(
            MessagesFeedUiState(messages = listOf(message("1", "hello"))),
            onPush = { pushed += it },
        )

        composeRule.onNodeWithTag(MessageCardTags.QUOTE).assertIsDisplayed()
        composeRule.onNodeWithTag(MessageCardTags.PUSH).performClick()

        assertThat(pushed.map { it.id }).containsExactly("1")
    }

    @Test
    fun push_isNotOffered_onYourOwnMessage() {
        setFeed(MessagesFeedUiState(messages = listOf(message("1", "hello", mine = true))))

        composeRule.onNodeWithTag(MessageCardTags.PUSH).assertDoesNotExist()
        composeRule.onNodeWithTag(MessageCardTags.QUOTE).assertDoesNotExist()
    }

    @Test
    fun push_isNotOffered_onAPushOfAPush() {
        setFeed(
            MessagesFeedUiState(messages = listOf(message("p1", "", pushedMessage = original()))),
        )

        composeRule.onNodeWithTag(MessageCardTags.QUOTE).assertDoesNotExist()
        // The count still shows, but never as a control that would fail on tap.
        composeRule.onNodeWithTag(MessageCardTags.PUSH).assertDoesNotExist()
    }

    @Test
    fun pushCount_isShownWithoutTheAction_whenTheMessageCannotBePushed() {
        setFeed(
            MessagesFeedUiState(
                messages = listOf(message("1", "popular", mine = true, pushCount = 3)),
            ),
        )

        composeRule.onNodeWithTag(MessageCardTags.PUSH).assertHasNoClickAction()
        composeRule.onNodeWithText("3").assertIsDisplayed()
    }

    @Test
    fun quote_asksToComposeAQuoteOfThatMessage() {
        val quoted = mutableListOf<Message>()
        setFeed(
            MessagesFeedUiState(messages = listOf(message("1", "the original post"))),
            onQuote = { quoted += it },
        )

        composeRule.onNodeWithTag(MessageCardTags.QUOTE).performClick()

        assertThat(quoted.map { it.id }).containsExactly("1")
    }

    @Test
    fun composer_showsTheQuotedMessage_andItsAlwaysPublicBanner() {
        setFeed(
            MessagesFeedUiState(
                isComposeOpen = true,
                quoteTarget = message("orig", "the original post"),
            ),
        )

        composeRule.onNodeWithTag(MessagesFeedTags.COMPOSE_INPUT).assertIsDisplayed()
        composeRule.onNodeWithTag(MessagesFeedTags.QUOTE_ATTACHED).assertIsDisplayed()
        composeRule.onNodeWithTag(MessagesFeedTags.QUOTE_PUBLIC_BANNER).assertIsDisplayed()
        composeRule.onNodeWithText("Pushes and quotes are always public.").assertIsDisplayed()
    }

    @Test
    fun composer_doesNotOfferPrivate_forAQuote() {
        setFeed(
            MessagesFeedUiState(
                isComposeOpen = true,
                composeText = "worth reading",
                quoteTarget = message("orig", "the original post"),
                composeVisibility = MessageVisibility.PUBLIC,
            ),
        )

        composeRule.onNodeWithTag(MessagesFeedTags.VISIBILITY_PRIVATE).assertDoesNotExist()
        // Public is shown, selected and locked.
        composeRule.onNodeWithTag(MessagesFeedTags.VISIBILITY_PUBLIC).assertIsSelected()
        composeRule.onNodeWithTag(MessagesFeedTags.VISIBILITY_PUBLIC).assertIsNotEnabled()
    }

    @Test
    fun composer_stillOffersPrivate_forAnOrdinaryMessage() {
        setFeed(
            MessagesFeedUiState(
                isComposeOpen = true,
                composeText = "hi",
                composeVisibility = MessageVisibility.PUBLIC,
            ),
        )

        composeRule.onNodeWithTag(MessagesFeedTags.VISIBILITY_PRIVATE).assertHasClickAction()
    }
}
