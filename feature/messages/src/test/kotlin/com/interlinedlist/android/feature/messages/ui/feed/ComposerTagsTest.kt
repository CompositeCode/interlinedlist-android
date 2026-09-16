package com.interlinedlist.android.feature.messages.ui.feed

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.messages.domain.TagSuggestion
import com.interlinedlist.android.feature.messages.ui.FakeMessagesRepository
import com.interlinedlist.android.feature.messages.ui.sampleMessage
import com.interlinedlist.android.feature.messages.ui.feed.MessagesFeedViewModel.Companion.TAG_SUGGESTION_DEBOUNCE_MS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The composer's tag input: what gets posted, and how prefix autocomplete behaves
 * while the user types.
 *
 * The autocomplete contract is deliberately strict, because getting it wrong is
 * invisible until it hurts: every keystroke supersedes the last (one request per
 * pause, not one per key), and a response that lost the race is discarded rather
 * than allowed to overwrite newer suggestions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ComposerTagsTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(repo: FakeMessagesRepository) = MessagesFeedViewModel(repo)

    /** Types [text] one character at a time, faster than the debounce window. */
    private fun MessagesFeedViewModel.typeFast(text: String) {
        text.indices.forEach { index -> onTagQueryChange(text.substring(0, index + 1)) }
    }

    // --- posting tags ------------------------------------------------------

    @Test
    fun `post sends the committed tags`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository().apply { createResult = ApiResult.Success(sampleMessage()) }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onComposeTextChange("tagged post")
        vm.onTagQueryChange("lists")
        vm.commitTag()
        vm.onTagQueryChange("llms")
        vm.commitTag()
        vm.post()
        advanceUntilIdle()

        assertThat(repo.lastCreate?.tags).containsExactly("lists", "llms").inOrder()
    }

    @Test
    fun `post sends no tags when none were added`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository().apply { createResult = ApiResult.Success(sampleMessage()) }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onComposeTextChange("plain post")
        vm.post()
        advanceUntilIdle()

        assertThat(repo.lastCreate?.tags).isEmpty()
    }

    @Test
    fun `a tag left uncommitted in the field is still posted`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository().apply { createResult = ApiResult.Success(sampleMessage()) }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onComposeTextChange("post")
        vm.onTagQueryChange("lists")
        vm.commitTag()
        // Typed, but the user hit Post without committing this one.
        vm.onTagQueryChange("  llms  ")
        vm.post()
        advanceUntilIdle()

        assertThat(repo.lastCreate?.tags).containsExactly("lists", "llms").inOrder()
    }

    @Test
    fun `a tag containing spaces and punctuation is kept whole`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository().apply { createResult = ApiResult.Success(sampleMessage()) }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onComposeTextChange("post")
        // A real tag from the live API: spaces and a comma, not a hashtag token.
        vm.onTagQueryChange("life is short, o brave girl")
        vm.commitTag()
        vm.post()
        advanceUntilIdle()

        assertThat(repo.lastCreate?.tags).containsExactly("life is short, o brave girl")
    }

    @Test
    fun `committing a tag clears the field and its suggestions`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository().apply {
            autocompleteResponses["lis"] = ApiResult.Success(listOf(TagSuggestion("lists", 8)))
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onTagQueryChange("lis")
        advanceUntilIdle()

        vm.uiState.test {
            advanceUntilIdle()
            assertThat(expectMostRecentItem().tagSuggestions).hasSize(1)

            vm.commitTag()
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertThat(state.composeTags).containsExactly("lis")
            assertThat(state.tagQuery).isEmpty()
            assertThat(state.tagSuggestions).isEmpty()
        }
    }

    @Test
    fun `the same tag is not added twice`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository()
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onTagQueryChange("lists")
        vm.commitTag()
        vm.onTagQueryChange("lists")
        vm.commitTag()
        advanceUntilIdle()

        vm.uiState.test {
            advanceUntilIdle()
            assertThat(expectMostRecentItem().composeTags).containsExactly("lists")
        }
    }

    @Test
    fun `a removed tag is not posted`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository().apply { createResult = ApiResult.Success(sampleMessage()) }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onComposeTextChange("post")
        vm.onTagQueryChange("keep")
        vm.commitTag()
        vm.onTagQueryChange("drop")
        vm.commitTag()
        vm.onRemoveTag("drop")
        vm.post()
        advanceUntilIdle()

        assertThat(repo.lastCreate?.tags).containsExactly("keep")
    }

    @Test
    fun `a successful post clears the tag state for the next compose`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository().apply { createResult = ApiResult.Success(sampleMessage()) }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onComposeTextChange("post")
        vm.onTagQueryChange("lists")
        vm.commitTag()
        vm.post()
        advanceUntilIdle()

        vm.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertThat(state.composeTags).isEmpty()
            assertThat(state.tagQuery).isEmpty()
        }
    }

    @Test
    fun `tapping a suggestion adds it exactly as the server spelled it`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository().apply { createResult = ApiResult.Success(sampleMessage()) }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onComposeTextChange("post")
        vm.onTagQueryChange("le")
        // The server answers case-insensitively: "le" can suggest "Lego".
        vm.onSelectTagSuggestion(TagSuggestion("Lego", 2))
        vm.post()
        advanceUntilIdle()

        assertThat(repo.lastCreate?.tags).containsExactly("Lego")
    }

    // --- autocomplete: debounce -------------------------------------------

    @Test
    fun `typing a word straight through issues a single request for the final prefix`() =
        runTest(dispatcher) {
            val repo = FakeMessagesRepository()
            val vm = viewModel(repo)
            advanceUntilIdle()

            vm.typeFast("list")
            advanceUntilIdle()

            assertThat(repo.autocompleteQueries).containsExactly("list")
        }

    @Test
    fun `no request goes out before the debounce window elapses`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository()
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onTagQueryChange("li")
        advanceTimeBy(TAG_SUGGESTION_DEBOUNCE_MS - 1)

        assertThat(repo.autocompleteQueries).isEmpty()

        advanceUntilIdle()
        assertThat(repo.autocompleteQueries).containsExactly("li")
    }

    @Test
    fun `a pause between words issues one request per word`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository()
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.typeFast("li")
        advanceUntilIdle()
        vm.typeFast("lists")
        advanceUntilIdle()

        assertThat(repo.autocompleteQueries).containsExactly("li", "lists").inOrder()
    }

    @Test
    fun `clearing the field asks for nothing and drops the suggestions`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository().apply {
            autocompleteResponses["li"] = ApiResult.Success(listOf(TagSuggestion("lists", 8)))
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onTagQueryChange("li")
        advanceUntilIdle()
        vm.onTagQueryChange("")
        advanceUntilIdle()

        // An empty `q` is a 400 from this endpoint; it must never be sent.
        assertThat(repo.autocompleteQueries).containsExactly("li")
        vm.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertThat(state.tagSuggestions).isEmpty()
            assertThat(state.isLoadingTagSuggestions).isFalse()
        }
    }

    @Test
    fun `suggestions are shown in the order the server returned them`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository().apply {
            // The live shape: ordered by count desc, case-insensitively matched,
            // and free to contain spaces and punctuation.
            autocompleteResponses["l"] = ApiResult.Success(
                listOf(
                    TagSuggestion("lists", 8),
                    TagSuggestion("llms", 5),
                    TagSuggestion("Lego", 2),
                    TagSuggestion("life is short, o brave girl", 2),
                ),
            )
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onTagQueryChange("l")
        advanceUntilIdle()

        vm.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertThat(state.tagSuggestions.map { it.tag })
                .containsExactly("lists", "llms", "Lego", "life is short, o brave girl").inOrder()
            assertThat(state.isLoadingTagSuggestions).isFalse()
        }
    }

    @Test
    fun `a failed lookup quietly leaves no suggestions and no feed error`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository().apply {
            autocompleteResult = ApiResult.Failure(AppError.Network("offline"))
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onTagQueryChange("li")
        advanceUntilIdle()

        vm.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertThat(state.tagSuggestions).isEmpty()
            assertThat(state.isLoadingTagSuggestions).isFalse()
            assertThat(state.errorMessage).isNull()
        }
    }

    // --- autocomplete: cancellation ---------------------------------------

    @Test
    fun `a keystroke cancels the request already in flight`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository().apply { gateAutocomplete("li") }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onTagQueryChange("li")
        advanceTimeBy(TAG_SUGGESTION_DEBOUNCE_MS + 1)
        assertThat(repo.autocompleteQueries).containsExactly("li") // in flight, gated

        vm.onTagQueryChange("lis")
        advanceUntilIdle()

        assertThat(repo.cancelledAutocompleteQueries).containsExactly("li")
        assertThat(repo.autocompleteQueries).containsExactly("li", "lis").inOrder()
    }

    @Test
    fun `closing the composer cancels the pending lookup`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository()
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.openCompose()
        vm.onTagQueryChange("li")
        vm.dismissCompose()
        advanceUntilIdle()

        assertThat(repo.autocompleteQueries).isEmpty()
        vm.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertThat(state.tagQuery).isEmpty()
            assertThat(state.composeTags).isEmpty()
            assertThat(state.tagSuggestions).isEmpty()
        }
    }

    @Test
    fun `a late response for an older prefix never overwrites a newer one`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository().apply {
            autocompleteResponses["li"] = ApiResult.Success(listOf(TagSuggestion("lists", 8)))
            autocompleteResponses["lego"] = ApiResult.Success(listOf(TagSuggestion("Lego", 2)))
            // "li" is already past the point of no return: the server will answer
            // it even though the caller has moved on.
            gateAutocomplete("li", ignoreCancellation = true)
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onTagQueryChange("li")
        advanceTimeBy(TAG_SUGGESTION_DEBOUNCE_MS + 1) // "li" is now in flight
        vm.onTagQueryChange("lego")
        advanceUntilIdle() // "lego" answers first

        vm.uiState.test {
            advanceUntilIdle()
            assertThat(expectMostRecentItem().tagSuggestions.map { it.tag }).containsExactly("Lego")

            // The overtaken "li" response finally lands...
            repo.releaseAutocomplete("li")
            advanceUntilIdle()
            // ...and changes nothing at all: no new state was published.
            expectNoEvents()
        }
        // It really did answer — it was discarded on arrival, not simply lost.
        assertThat(repo.completedAutocompleteQueries).containsExactly("lego", "li").inOrder()
        assertThat(vm.uiState.value.tagSuggestions.map { it.tag }).containsExactly("Lego")
    }
}
