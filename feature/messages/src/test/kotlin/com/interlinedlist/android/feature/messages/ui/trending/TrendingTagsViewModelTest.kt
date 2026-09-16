package com.interlinedlist.android.feature.messages.ui.trending

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.messages.domain.TrendingTag
import com.interlinedlist.android.feature.messages.domain.TrendingWindow
import com.interlinedlist.android.feature.messages.navigation.MessagesDestinations
import com.interlinedlist.android.feature.messages.ui.FakeMessagesRepository
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
import java.net.URLDecoder

/**
 * The trending-tags surface.
 *
 * Its whole job is to be a set of doors: every state it can be in has to look
 * deliberate (a quiet instance has no trending tags, and that is not a bug), and
 * the tag behind each door has to reach the tag feed byte-for-byte — trending
 * tags are free-form labels with spaces and commas in them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrendingTagsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    /** A real tag from the live site: spaces *and* a comma, in one label. */
    private val awkwardTag = "life is short, o brave girl"

    private fun repo(result: ApiResult<List<TrendingTag>>) =
        FakeMessagesRepository().apply { trendingTagsResult = result }

    @Test
    fun `loads the trending tags on open, in the server's order`() = runTest(dispatcher) {
        val repo = repo(
            ApiResult.Success(
                listOf(
                    TrendingTag("Lego", count = 2, lastUsedAt = "2026-09-12T20:40:05.777Z"),
                    TrendingTag(awkwardTag, count = 1, lastUsedAt = "2026-09-11T03:44:52.334Z"),
                ),
            ),
        )
        val vm = TrendingTagsViewModel(repo)

        vm.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertThat(state.status).isEqualTo(TrendingTagsStatus.TAGS)
            // The server ranks them; the rail never re-sorts or re-labels.
            assertThat(state.tags.map { it.tag })
                .containsExactly("Lego", awkwardTag).inOrder()
        }
    }

    @Test
    fun `starts in a loading state rather than claiming there is nothing`() = runTest(dispatcher) {
        val vm = TrendingTagsViewModel(repo(ApiResult.Success(emptyList())))

        vm.uiState.test {
            assertThat(awaitItem().status).isEqualTo(TrendingTagsStatus.LOADING)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an empty answer is an empty state, not a blank`() = runTest(dispatcher) {
        val vm = TrendingTagsViewModel(repo(ApiResult.Success(emptyList())))

        vm.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            // A new or quiet instance legitimately has no trending tags: the
            // surface must say so instead of rendering nothing at all.
            assertThat(state.status).isEqualTo(TrendingTagsStatus.EMPTY)
            assertThat(state.errorMessage).isNull()
        }
    }

    @Test
    fun `a failure is an error state, distinct from empty`() = runTest(dispatcher) {
        val vm = TrendingTagsViewModel(repo(ApiResult.Failure(AppError.Network("offline"))))

        vm.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertThat(state.status).isEqualTo(TrendingTagsStatus.ERROR)
            assertThat(state.status).isNotEqualTo(TrendingTagsStatus.EMPTY)
            assertThat(state.errorMessage).isNotNull()
            assertThat(state.tags).isEmpty()
        }
    }

    @Test
    fun `retry asks again and recovers`() = runTest(dispatcher) {
        val repo = repo(ApiResult.Failure(AppError.Network("offline")))
        val vm = TrendingTagsViewModel(repo)
        advanceUntilIdle()

        repo.trendingTagsResult = ApiResult.Success(listOf(TrendingTag("lists", count = 6)))
        vm.refresh()
        advanceUntilIdle()

        vm.uiState.test {
            val state = expectMostRecentItem()
            assertThat(state.status).isEqualTo(TrendingTagsStatus.TAGS)
            assertThat(state.errorMessage).isNull()
        }
        assertThat(repo.trendingWindows).hasSize(2)
    }

    @Test
    fun `asks for the window it labels the surface with`() = runTest(dispatcher) {
        val repo = repo(ApiResult.Success(emptyList()))
        val vm = TrendingTagsViewModel(repo)
        advanceUntilIdle()

        // The response carries no window metadata, so the request is the only
        // thing that makes this wording true.
        assertThat(repo.trendingWindows).containsExactly(TrendingWindow.WEEK)
        assertThat(vm.uiState.value.window).isEqualTo(TrendingWindow.WEEK)
        assertThat(vm.uiState.value.title).isEqualTo("Trending this week")
    }

    @Test
    fun `a tapped trending tag routes to that tag's feed, byte-for-byte`() = runTest(dispatcher) {
        val vm = TrendingTagsViewModel(repo(ApiResult.Success(listOf(TrendingTag(awkwardTag)))))
        advanceUntilIdle()

        val tapped = vm.uiState.value.tags.single().tag
        val route = MessagesDestinations.tagFeedRoute(tapped)

        assertThat(route).isEqualTo("messages/tag/life%20is%20short%2C%20o%20brave%20girl")
        // What the nav argument hands the tag feed is the tag the API gave us.
        val decoded = URLDecoder.decode(route.removePrefix("messages/tag/"), Charsets.UTF_8.name())
        assertThat(decoded).isEqualTo(awkwardTag)
    }
}
