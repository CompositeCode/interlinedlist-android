package com.interlinedlist.android.feature.messages.ui.feed

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.messages.ui.FakeMessagesRepository
import com.interlinedlist.android.feature.messages.ui.sampleMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MessagesFeedViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `feed emits cached messages from the repository`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository()
        repo.emitFeed(listOf(sampleMessage(id = "1"), sampleMessage(id = "2")))
        val vm = MessagesFeedViewModel(repo)

        vm.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertThat(state.messages.map { it.id }).containsExactly("1", "2").inOrder()
            assertThat(state.isRefreshing).isFalse()
        }
    }

    @Test
    fun `refresh runs on init and toggles the refreshing flag`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository().apply { refreshResult = ApiResult.Success(true) }
        val vm = MessagesFeedViewModel(repo)

        vm.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertThat(repo.refreshCount).isEqualTo(1)
            assertThat(state.isRefreshing).isFalse()
            assertThat(state.canLoadMore).isTrue()
        }
    }

    @Test
    fun `refresh failure surfaces a mapped error`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository().apply {
            refreshResult = ApiResult.Failure(AppError.Network("offline"))
        }
        val vm = MessagesFeedViewModel(repo)

        vm.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertThat(state.errorMessage).isEqualTo("No connection. Check your network and try again.")
            assertThat(state.subscriptionRequired).isFalse()
        }
    }

    @Test
    fun `subscription-gated refresh sets the locked flag`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository().apply {
            refreshResult = ApiResult.Failure(AppError.SubscriptionRequired("Subscribers only"))
        }
        val vm = MessagesFeedViewModel(repo)

        vm.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertThat(state.subscriptionRequired).isTrue()
            assertThat(state.errorMessage).isEqualTo("Subscribers only")
        }
    }

    @Test
    fun `loadMore is a no-op when there are no more pages`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository().apply { refreshResult = ApiResult.Success(false) }
        val vm = MessagesFeedViewModel(repo)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.loadMore()
        advanceUntilIdle()

        assertThat(repo.loadMoreCount).isEqualTo(0)
    }

    @Test
    fun `loadMore fetches the next page when more are available`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository().apply {
            refreshResult = ApiResult.Success(true)
            loadMoreResult = ApiResult.Success(false)
        }
        val vm = MessagesFeedViewModel(repo)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.loadMore()
        advanceUntilIdle()

        assertThat(repo.loadMoreCount).isEqualTo(1)
        assertThat(vm.uiState.value.canLoadMore).isFalse()
    }

    @Test
    fun `post creates a message and closes the compose sheet`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository().apply {
            createResult = ApiResult.Success(sampleMessage(id = "new"))
        }
        val vm = MessagesFeedViewModel(repo)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.openCompose()
        vm.onComposeTextChange("hello world")
        vm.post()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.isComposeOpen).isFalse()
        assertThat(state.composeText).isEmpty()
        assertThat(state.isPosting).isFalse()
    }

    @Test
    fun `post failure keeps the sheet open and shows an error`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository().apply {
            createResult = ApiResult.Failure(AppError.Server("nope"))
        }
        val vm = MessagesFeedViewModel(repo)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.openCompose()
        vm.onComposeTextChange("hello")
        vm.post()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.isPosting).isFalse()
        assertThat(state.errorMessage).isNotEmpty()
    }

    @Test
    fun `dig delegates to the repository with the toggled value`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository()
        val vm = MessagesFeedViewModel(repo)
        advanceUntilIdle()

        vm.onDig(sampleMessage(id = "42", dugByMe = false))
        advanceUntilIdle()

        assertThat(repo.lastSetDug).isEqualTo("42" to true)
    }

    @Test
    fun `delete delegates to the repository`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository()
        val vm = MessagesFeedViewModel(repo)
        advanceUntilIdle()

        vm.onDelete(sampleMessage(id = "9", mine = true))
        advanceUntilIdle()

        assertThat(repo.deletedIds).containsExactly("9")
    }
}
