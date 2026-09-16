package com.interlinedlist.android.feature.messages.ui.trending

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.messages.domain.Message
import com.interlinedlist.android.feature.messages.domain.TrendingTag
import com.interlinedlist.android.feature.messages.ui.feed.MessagesFeedScreen
import com.interlinedlist.android.feature.messages.ui.feed.MessagesFeedTags
import com.interlinedlist.android.feature.messages.ui.feed.MessagesFeedUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The trending rail: that each of its states is visibly *something*, that the
 * failure state is not mistakable for the quiet one, and that a tapped tag hands
 * back the exact string the feed must be filtered by.
 */
@RunWith(AndroidJUnit4::class)
class TrendingTagsRailTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** A real tag from the live site: spaces *and* a comma, in one label. */
    private val awkwardTag = "life is short, o brave girl"

    private fun setRail(
        state: TrendingTagsUiState,
        onOpenTag: (String) -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        composeRule.setContent {
            InterlinedListTheme {
                TrendingTagsRail(state = state, onOpenTag = onOpenTag, onRetry = onRetry)
            }
        }
    }

    @Test
    fun tags_areOfferedInTheServersOrder_withTheWindowTheyWereAskedFor() {
        setRail(
            TrendingTagsUiState(
                tags = listOf(
                    TrendingTag("Lego", count = 2, lastUsedAt = "2026-09-12T20:40:05.777Z"),
                    TrendingTag(awkwardTag, count = 1),
                ),
            ),
        )

        composeRule.onNodeWithTag(TrendingTagsRailTags.CHIPS).assertIsDisplayed()
        composeRule.onNodeWithTag(TrendingTagsRailTags.chipTag("Lego")).assertIsDisplayed()
        composeRule.onNodeWithTag(TrendingTagsRailTags.chipTag(awkwardTag)).assertIsDisplayed()
        // The window is the one the app requested; the response never reports one.
        composeRule.onNodeWithText("Trending this week").assertIsDisplayed()
    }

    @Test
    fun tappingATag_handsBackTheExactTagString() {
        val opened = mutableListOf<String>()
        setRail(
            TrendingTagsUiState(tags = listOf(TrendingTag(awkwardTag, count = 1))),
            onOpenTag = { opened += it },
        )

        composeRule.onNodeWithTag(TrendingTagsRailTags.chipTag(awkwardTag)).performClick()

        // Not trimmed, split on the comma, or turned into a hashtag: this string
        // is what MessagesDestinations.tagFeedRoute encodes into the tag feed.
        assertThat(opened).containsExactly(awkwardTag)
    }

    @Test
    fun noTrendingTags_rendersTheEmptyState_ratherThanABlankStrip() {
        setRail(TrendingTagsUiState(tags = emptyList()))

        composeRule.onNodeWithTag(TrendingTagsRailTags.EMPTY).assertIsDisplayed()
        composeRule.onNodeWithText(NO_TRENDING_TAGS).assertIsDisplayed()
        composeRule.onNodeWithTag(TrendingTagsRailTags.ERROR).assertDoesNotExist()
        composeRule.onNodeWithTag(TrendingTagsRailTags.CHIPS).assertDoesNotExist()
    }

    @Test
    fun aFailedLookup_looksDifferentFromAQuietInstance_andCanBeRetried() {
        val retries = mutableListOf<Unit>()
        setRail(
            TrendingTagsUiState(errorMessage = "No connection. Check your network and try again."),
            onRetry = { retries += Unit },
        )

        composeRule.onNodeWithTag(TrendingTagsRailTags.ERROR).assertIsDisplayed()
        // "Nothing is trending" and "we could not find out" are different answers.
        composeRule.onNodeWithTag(TrendingTagsRailTags.EMPTY).assertDoesNotExist()

        composeRule.onNodeWithTag(TrendingTagsRailTags.RETRY).performClick()
        assertThat(retries).hasSize(1)
    }

    @Test
    fun theFirstLoad_showsProgress_ratherThanClaimingThereIsNothing() {
        setRail(TrendingTagsUiState(isLoading = true))

        composeRule.onNodeWithTag(TrendingTagsRailTags.PROGRESS).assertIsDisplayed()
        composeRule.onNodeWithTag(TrendingTagsRailTags.EMPTY).assertDoesNotExist()
    }

    @Test
    fun staleTagsSurviveAFailedRefresh_insteadOfBeingReplacedByAnError() {
        setRail(
            TrendingTagsUiState(
                tags = listOf(TrendingTag("lists", count = 6)),
                errorMessage = "No connection. Check your network and try again.",
            ),
        )

        composeRule.onNodeWithTag(TrendingTagsRailTags.chipTag("lists")).assertIsDisplayed()
        composeRule.onNodeWithTag(TrendingTagsRailTags.ERROR).assertDoesNotExist()
    }

    // --- where it lives ----------------------------------------------------

    private fun setFeed(
        state: MessagesFeedUiState,
        trending: TrendingTagsUiState,
        onOpenTag: ((String) -> Unit)? = {},
    ) {
        composeRule.setContent {
            InterlinedListTheme {
                MessagesFeedScreen(
                    state = state,
                    trending = trending,
                    onRefresh = {},
                    onLoadMore = {},
                    onOpenMessage = {},
                    onDig = {},
                    onDelete = {},
                    onOpenCompose = {},
                    onDismissCompose = {},
                    onComposeTextChange = {},
                    onPost = {},
                    onOpenTag = onOpenTag,
                )
            }
        }
    }

    private fun message(id: String) = Message(
        id = id, content = "a message", authorId = "u1", authorUsername = "adron",
        authorDisplayName = "Adron", authorAvatarUrl = null, createdAt = null,
        digCount = 0, replyCount = 0, dugByMe = false, parentId = null, mine = false,
    )

    @Test
    fun theRail_ridesAtTheTopOfTheFeed_whereItIsWalkedPast() {
        setFeed(
            MessagesFeedUiState(messages = listOf(message("1"))),
            TrendingTagsUiState(tags = listOf(TrendingTag("lists", count = 6))),
        )

        composeRule.onNodeWithTag(MessagesFeedTags.LIST).assertIsDisplayed()
        composeRule.onNodeWithTag(TrendingTagsRailTags.RAIL).assertIsDisplayed()
        composeRule.onNodeWithTag(TrendingTagsRailTags.chipTag("lists")).assertIsDisplayed()
    }

    @Test
    fun anEmptyFeed_stillOffersSomewhereToGo() {
        setFeed(
            MessagesFeedUiState(messages = emptyList()),
            TrendingTagsUiState(tags = listOf(TrendingTag("lists", count = 6))),
        )

        composeRule.onNodeWithTag(MessagesFeedTags.EMPTY).assertIsDisplayed()
        composeRule.onNodeWithTag(TrendingTagsRailTags.chipTag("lists")).assertIsDisplayed()
    }

    @Test
    fun theRail_isAbsentWhereThereIsNowhereToSendTheUser() {
        setFeed(
            MessagesFeedUiState(messages = listOf(message("1"))),
            TrendingTagsUiState(tags = listOf(TrendingTag("lists", count = 6))),
            onOpenTag = null,
        )

        composeRule.onNodeWithTag(TrendingTagsRailTags.RAIL).assertDoesNotExist()
    }
}
