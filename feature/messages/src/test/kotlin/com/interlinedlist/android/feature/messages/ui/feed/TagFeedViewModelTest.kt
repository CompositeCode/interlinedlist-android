package com.interlinedlist.android.feature.messages.ui.feed

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.model.ViewingPreference
import com.interlinedlist.android.feature.messages.navigation.MessagesDestinations
import com.interlinedlist.android.feature.messages.ui.FakeMessagesRepository
import com.interlinedlist.android.feature.messages.ui.sampleMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The tag feed is the *same* ViewModel as the main feed, told which tag it is
 * showing through its nav argument. These tests pin that the tag reaches every
 * feed call — including the second page — and that the main feed is unaffected
 * when there is no tag argument at all.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TagFeedViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    /** A real tag from the live site: spaces *and* a comma, in one label. */
    private val awkwardTag = "life is short, o brave girl"

    private fun handle(tag: String) =
        SavedStateHandle(mapOf(MessagesDestinations.ARG_TAG to tag))

    @Test
    fun `a tag feed refreshes under its tag, exactly as given`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository()
        val vm = MessagesFeedViewModel(repo, handle(awkwardTag))

        vm.uiState.test {
            advanceUntilIdle()
            assertThat(expectMostRecentItem().tag).isEqualTo(awkwardTag)
        }
        // Not trimmed, lowercased or split: the tag the card carried is the tag
        // the query runs under.
        assertThat(repo.refreshTags).containsExactly(awkwardTag)
    }

    @Test
    fun `the main feed asks for no tag`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository()
        val vm = MessagesFeedViewModel(repo)

        vm.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertThat(state.tag).isNull()
            assertThat(state.isTagFeed).isFalse()
        }
        assertThat(repo.refreshTags).containsExactly(null)
    }

    @Test
    fun `the tag feed shows the tag's own cached rows, not the main feed's`() =
        runTest(dispatcher) {
            val repo = FakeMessagesRepository()
            repo.emitFeed(listOf(sampleMessage(id = "main")))
            repo.emitTagFeed("lists", listOf(sampleMessage(id = "tagged", tags = listOf("lists"))))
            val vm = MessagesFeedViewModel(repo, handle("lists"))

            vm.uiState.test {
                advanceUntilIdle()
                assertThat(expectMostRecentItem().messages.map { it.id }).containsExactly("tagged")
            }
        }

    @Test
    fun `paging a tag feed carries the cursor and the tag together`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository().apply {
            refreshResult = ApiResult.Success("opaque-cursor")
            loadMoreResult = ApiResult.Success(null)
        }
        val vm = MessagesFeedViewModel(repo, handle("lists"))

        vm.uiState.test {
            advanceUntilIdle()
            assertThat(expectMostRecentItem().canLoadMore).isTrue()
            vm.loadMore()
            advanceUntilIdle()
            assertThat(expectMostRecentItem().canLoadMore).isFalse()
        }

        assertThat(repo.loadMoreCursors).containsExactly("opaque-cursor")
        assertThat(repo.loadMoreTags).containsExactly("lists")
    }

    @Test
    fun `switching the view preference on a tag feed reloads it under the same tag`() =
        runTest(dispatcher) {
            val repo = FakeMessagesRepository()
            val vm = MessagesFeedViewModel(repo, handle("lists"))

            vm.uiState.test {
                advanceUntilIdle()
                vm.onViewingPreferenceChange(ViewingPreference.MINE)
                advanceUntilIdle()
                assertThat(expectMostRecentItem().viewingPreference).isEqualTo(ViewingPreference.MINE)
            }

            // The view switcher is shared with the main feed, so the tag feed keeps
            // honouring it — and never loses its tag doing so.
            assertThat(repo.savedViewingPreferences).containsExactly(ViewingPreference.MINE)
            assertThat(repo.refreshTags).containsExactly("lists", "lists").inOrder()
            assertThat(repo.refreshPreferences.last()).isEqualTo(ViewingPreference.MINE)
        }

    @Test
    fun `a blank tag argument is treated as the main feed`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository()
        val vm = MessagesFeedViewModel(repo, handle("   "))

        vm.uiState.test {
            advanceUntilIdle()
            assertThat(expectMostRecentItem().isTagFeed).isFalse()
        }
        assertThat(repo.refreshTags).containsExactly(null)
    }

    @Test
    fun `a tag feed does not set up the composer it never shows`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository()
        val vm = MessagesFeedViewModel(repo, handle("lists"))

        vm.uiState.test {
            advanceUntilIdle()
            assertThat(expectMostRecentItem().linkedNetworks).isEmpty()
        }
        // The cross-post destinations and default visibility are composer-only
        // lookups; a screen with no composer should not pay for them.
        assertThat(repo.linkedNetworksCalls).isEqualTo(0)
        assertThat(repo.defaultVisibilityCalls).isEqualTo(0)
    }
}
