package com.interlinedlist.android.feature.directmessages.ui.thread

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.directmessages.data.DirectMessage
import com.interlinedlist.android.feature.directmessages.ui.FakeDirectMessagesRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
class ThreadViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun message(id: String, mine: Boolean, body: String = "hi") = DirectMessage(
        id = id, conversationUsername = "adron",
        senderId = if (mine) "me" else "other",
        recipientId = if (mine) "other" else "me",
        body = body, imageUrls = emptyList(), createdAt = "2026-07-31T10:00:00Z",
        createdAtMillis = id.hashCode().toLong(), readAt = null, pending = false,
    )

    private fun vm(repo: FakeDirectMessagesRepository, pollMillis: Long = 3_000L) =
        ThreadViewModel(repo, "adron", pollIntervalMillis = pollMillis)

    @Test
    fun `marks mine vs theirs from the current user id`() = runTest(dispatcher) {
        val repo = FakeDirectMessagesRepository(currentUserId = "me")
        val vm = vm(repo)

        vm.uiState.test {
            awaitItem()
            repo.threadFlow.value = listOf(message("m1", mine = false), message("m2", mine = true))
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertThat(state.messages).hasSize(2)
            assertThat(state.messages.first { it.id == "m1" }.isMine).isFalse()
            assertThat(state.messages.first { it.id == "m2" }.isMine).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loads the thread on init`() = runTest(dispatcher) {
        val repo = FakeDirectMessagesRepository()
        vm(repo)
        advanceUntilIdle()
        assertThat(repo.refreshThreadCount).isEqualTo(1)
    }

    @Test
    fun `send clears the draft and delegates to the repository`() = runTest(dispatcher) {
        val repo = FakeDirectMessagesRepository()
        val vm = vm(repo)
        advanceUntilIdle()

        vm.onDraftChange("hello there")
        vm.send()
        advanceUntilIdle()

        assertThat(repo.sentBodies).containsExactly("hello there")
        assertThat(vm.uiState.value.draft).isEmpty()
    }

    @Test
    fun `send does nothing for a blank draft`() = runTest(dispatcher) {
        val repo = FakeDirectMessagesRepository()
        val vm = vm(repo)
        advanceUntilIdle()

        vm.onDraftChange("   ")
        vm.send()
        advanceUntilIdle()

        assertThat(repo.sentBodies).isEmpty()
    }

    @Test
    fun `failed send restores the draft and shows an error`() = runTest(dispatcher) {
        val repo = FakeDirectMessagesRepository()
        repo.sendResult = ApiResult.Failure(AppError.Network("offline"))
        val vm = vm(repo)
        advanceUntilIdle()

        vm.onDraftChange("retry me")
        vm.send()
        advanceUntilIdle()

        assertThat(vm.uiState.value.errorMessage).isNotNull()
        assertThat(vm.uiState.value.draft).isEqualTo("retry me")
    }

    @Test
    fun `does not poll until polling is started`() = runTest(dispatcher) {
        val repo = FakeDirectMessagesRepository()
        val vm = vm(repo, pollMillis = 3_000L)
        advanceUntilIdle() // initial refreshThread only

        assertThat(repo.pollCount).isEqualTo(0)
    }

    @Test
    fun `polls for updates on the configured interval while started`() = runTest(dispatcher) {
        val repo = FakeDirectMessagesRepository()
        repo.pollResult = ApiResult.Success(1)
        val vm = vm(repo, pollMillis = 3_000L)
        advanceUntilIdle() // initial refreshThread

        vm.startPolling()

        // One interval → one poll; two intervals → two polls. Stop before draining
        // so the infinite poll loop doesn't hang advanceUntilIdle().
        advanceTimeBy(3_100L)
        assertThat(repo.pollCount).isEqualTo(1)

        advanceTimeBy(3_000L)
        assertThat(repo.pollCount).isEqualTo(2)

        vm.stopPolling()
        advanceTimeBy(6_000L)
        assertThat(repo.pollCount).isEqualTo(2) // no further polls after stop
    }
}
