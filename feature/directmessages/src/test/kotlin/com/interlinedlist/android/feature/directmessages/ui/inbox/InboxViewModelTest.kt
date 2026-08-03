package com.interlinedlist.android.feature.directmessages.ui.inbox

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.directmessages.data.Conversation
import com.interlinedlist.android.feature.directmessages.ui.FakeDirectMessagesRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
class InboxViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun conversation(username: String, unread: Boolean) = Conversation(
        username = username, displayName = username, avatarUrl = null,
        lastMessageBody = "hey", lastMessageAtMillis = 1L, hasUnread = unread,
    )

    @Test
    fun `starts empty then reflects cached conversations`() = runTest(dispatcher) {
        val repo = FakeDirectMessagesRepository()
        val vm = InboxViewModel(repo)

        vm.uiState.test {
            assertThat(awaitItem().conversations).isEmpty()

            repo.conversationsFlow.value = listOf(conversation("adron", unread = true))
            advanceUntilIdle()

            val loaded = awaitItem()
            assertThat(loaded.conversations).hasSize(1)
            assertThat(loaded.conversations.first().username).isEqualTo("adron")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `unread badge counts only conversations with unread`() = runTest(dispatcher) {
        val repo = FakeDirectMessagesRepository()
        val vm = InboxViewModel(repo)

        vm.uiState.test {
            awaitItem()
            repo.conversationsFlow.value = listOf(
                conversation("adron", unread = true),
                conversation("blake", unread = false),
                conversation("casey", unread = true),
            )
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertThat(state.unreadConversationCount).isEqualTo(2)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refresh loads the first page and stores the next cursor`() = runTest(dispatcher) {
        val repo = FakeDirectMessagesRepository()
        repo.refreshInboxResult = ApiResult.Success("next")
        val vm = InboxViewModel(repo)

        // The ViewModel refreshes once on init; refresh again explicitly.
        vm.refresh()
        advanceUntilIdle()

        assertThat(repo.refreshInboxCount).isEqualTo(2)
        assertThat(vm.uiState.value.isRefreshing).isFalse()
        assertThat(vm.uiState.value.nextCursor).isEqualTo("next")
    }

    @Test
    fun `refresh failure surfaces an error message`() = runTest(dispatcher) {
        val repo = FakeDirectMessagesRepository()
        repo.refreshInboxResult = ApiResult.Failure(AppError.Network("offline"))
        val vm = InboxViewModel(repo)

        vm.refresh()
        advanceUntilIdle()

        assertThat(vm.uiState.value.isRefreshing).isFalse()
        assertThat(vm.uiState.value.errorMessage).isNotNull()
    }

    @Test
    fun `loadMore pages using the stored cursor`() = runTest(dispatcher) {
        val repo = FakeDirectMessagesRepository()
        repo.refreshInboxResult = ApiResult.Success("cursor-2")
        val vm = InboxViewModel(repo)

        // The ViewModel refreshes once on init, storing the first cursor.
        advanceUntilIdle()
        assertThat(vm.uiState.value.nextCursor).isEqualTo("cursor-2")
        val countAfterInit = repo.refreshInboxCount

        repo.refreshInboxResult = ApiResult.Success(null)
        vm.loadMore()
        advanceUntilIdle()

        assertThat(repo.refreshInboxCount).isEqualTo(countAfterInit + 1)
        assertThat(vm.uiState.value.nextCursor).isNull()
    }
}
