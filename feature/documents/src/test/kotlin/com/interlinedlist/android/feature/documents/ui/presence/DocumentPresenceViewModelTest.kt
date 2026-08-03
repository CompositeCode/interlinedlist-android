package com.interlinedlist.android.feature.documents.ui.presence

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.documents.domain.Presence
import com.interlinedlist.android.feature.documents.ui.FakeDocumentsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Presence is a long-running heartbeat loop, so these tests drive virtual time
 * explicitly with [runCurrent]/[advanceTimeBy] and always [DocumentPresenceViewModel.stop]
 * before the test ends — never `advanceUntilIdle()`, which would never settle while
 * the loop is active.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DocumentPresenceViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private fun presence(userId: String) = Presence(userId, "Name $userId", userId, null)

    private fun viewModel(repo: FakeDocumentsRepository) =
        DocumentPresenceViewModel(
            repo, SavedStateHandle(mapOf(PRESENCE_DOCUMENT_ID_ARG to "D1")),
        )

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `start sends a heartbeat and exposes participants`() = runTest(dispatcher) {
        val repo = FakeDocumentsRepository().apply {
            sendPresenceResult = ApiResult.Success(listOf(presence("u1"), presence("u2")))
        }
        val vm = viewModel(repo)

        vm.start()
        runCurrent()

        assertThat(repo.sendPresenceCount).isAtLeast(1)
        assertThat(vm.uiState.value.participants.map { it.userId }).containsExactly("u1", "u2")

        vm.stop()
        runCurrent()
    }

    @Test
    fun `heartbeats repeat on the interval while open`() = runTest(dispatcher) {
        val repo = FakeDocumentsRepository().apply {
            sendPresenceResult = ApiResult.Success(listOf(presence("u1")))
        }
        val vm = viewModel(repo)

        vm.start()
        runCurrent()
        val first = repo.sendPresenceCount

        advanceTimeBy(DocumentPresenceViewModel.HEARTBEAT_INTERVAL_MS + 100)
        runCurrent()

        assertThat(repo.sendPresenceCount).isGreaterThan(first)

        vm.stop()
        runCurrent()
    }

    @Test
    fun `stop sends a leave and halts further heartbeats`() = runTest(dispatcher) {
        val repo = FakeDocumentsRepository().apply {
            sendPresenceResult = ApiResult.Success(listOf(presence("u1")))
        }
        val vm = viewModel(repo)
        vm.start()
        runCurrent()

        vm.stop()
        runCurrent()
        val afterStop = repo.sendPresenceCount

        assertThat(repo.leavePresenceCount).isEqualTo(1)

        advanceTimeBy(DocumentPresenceViewModel.HEARTBEAT_INTERVAL_MS * 3)
        runCurrent()
        // No more heartbeats after stop.
        assertThat(repo.sendPresenceCount).isEqualTo(afterStop)
        assertThat(vm.uiState.value.participants).isEmpty()
    }
}
