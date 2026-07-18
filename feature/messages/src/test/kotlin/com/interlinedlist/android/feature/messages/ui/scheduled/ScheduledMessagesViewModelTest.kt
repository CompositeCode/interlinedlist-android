package com.interlinedlist.android.feature.messages.ui.scheduled

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
class ScheduledMessagesViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `refreshes on init and emits the cached scheduled messages`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository()
        repo.emitScheduled(listOf(sampleMessage(id = "s1", scheduledAt = "2026-07-19T09:00:00Z")))
        val vm = ScheduledMessagesViewModel(repo)

        vm.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertThat(repo.refreshScheduledCount).isEqualTo(1)
            assertThat(state.messages.map { it.id }).containsExactly("s1")
            assertThat(state.isRefreshing).isFalse()
        }
    }

    @Test
    fun `refresh failure surfaces a mapped error`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository().apply {
            refreshScheduledResult = ApiResult.Failure(AppError.Network("offline"))
        }
        val vm = ScheduledMessagesViewModel(repo)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertThat(vm.uiState.value.errorMessage).isEqualTo("No connection. Check your network and try again.")
    }

    @Test
    fun `cancel delegates to the repository`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository()
        val vm = ScheduledMessagesViewModel(repo)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.cancel(sampleMessage(id = "s7", scheduledAt = "2026-07-19T09:00:00Z"))
        advanceUntilIdle()

        assertThat(repo.cancelledScheduledIds).containsExactly("s7")
    }

    @Test
    fun `cancel failure surfaces an error`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository().apply {
            cancelScheduledResult = ApiResult.Failure(AppError.Server("boom"))
        }
        val vm = ScheduledMessagesViewModel(repo)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.cancel(sampleMessage(id = "s7", scheduledAt = "2026-07-19T09:00:00Z"))
        advanceUntilIdle()

        assertThat(vm.uiState.value.errorMessage).isNotEmpty()
    }
}
