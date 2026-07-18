package com.interlinedlist.android.feature.messages.ui.detail

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.messages.domain.ReportReason
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
class MessageDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun handle(id: String = "m1") = SavedStateHandle(mapOf(MESSAGE_ID_ARG to id))

    @Test
    fun `load fetches the message and its replies`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository().apply {
            fetchResult = ApiResult.Success(sampleMessage(id = "m1"))
        }
        repo.emitMessage(sampleMessage(id = "m1"))
        repo.emitReplies("m1", listOf(sampleMessage(id = "r1", parentId = "m1")))
        val vm = MessageDetailViewModel(repo, handle("m1"))
        backgroundScope.launch { vm.uiState.collect {} }

        advanceUntilIdle()
        val state = vm.uiState.value
        assertThat(state.message?.id).isEqualTo("m1")
        assertThat(state.replies.map { it.id }).containsExactly("r1")
        assertThat(state.isLoading).isFalse()
    }

    @Test
    fun `fetch failure surfaces an error and stops loading`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository().apply {
            fetchResult = ApiResult.Failure(AppError.NotFound("gone"))
        }
        val vm = MessageDetailViewModel(repo, handle("m1"))
        backgroundScope.launch { vm.uiState.collect {} }

        advanceUntilIdle()
        val state = vm.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.errorMessage).isEqualTo("This message is no longer available.")
    }

    @Test
    fun `subscription-gated fetch sets the locked flag`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository().apply {
            fetchResult = ApiResult.Failure(AppError.SubscriptionRequired("Subscribers only"))
        }
        val vm = MessageDetailViewModel(repo, handle("m1"))
        backgroundScope.launch { vm.uiState.collect {} }

        advanceUntilIdle()
        assertThat(vm.uiState.value.subscriptionRequired).isTrue()
    }

    @Test
    fun `postReply clears the input on success`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository().apply {
            fetchResult = ApiResult.Success(sampleMessage(id = "m1"))
            postReplyResult = ApiResult.Success(sampleMessage(id = "r1", parentId = "m1"))
        }
        val vm = MessageDetailViewModel(repo, handle("m1"))
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onReplyTextChange("nice thread")
        vm.postReply()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.replyText).isEmpty()
        assertThat(state.isPostingReply).isFalse()
    }

    @Test
    fun `postReply failure keeps the draft and shows an error`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository().apply {
            fetchResult = ApiResult.Success(sampleMessage(id = "m1"))
            postReplyResult = ApiResult.Failure(AppError.Server("boom"))
        }
        val vm = MessageDetailViewModel(repo, handle("m1"))
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onReplyTextChange("draft")
        vm.postReply()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.replyText).isEqualTo("draft")
        assertThat(state.errorMessage).isNotEmpty()
    }

    @Test
    fun `dig toggles the current message's dig via the repository`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository().apply {
            fetchResult = ApiResult.Success(sampleMessage(id = "m1", dugByMe = false))
        }
        repo.emitMessage(sampleMessage(id = "m1", dugByMe = false))
        val vm = MessageDetailViewModel(repo, handle("m1"))
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onDig()
        advanceUntilIdle()

        assertThat(repo.lastSetDug).isEqualTo("m1" to true)
    }

    @Test
    fun `report opens the dialog and submits via the repository`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository().apply {
            fetchResult = ApiResult.Success(sampleMessage(id = "m1"))
        }
        val vm = MessageDetailViewModel(repo, handle("m1"))
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val reply = sampleMessage(id = "r1", parentId = "m1")
        vm.openReport(reply)
        advanceUntilIdle()
        assertThat(vm.uiState.value.reportTarget?.id).isEqualTo("r1")

        vm.submitReport(ReportReason.SPAM, "")
        advanceUntilIdle()

        assertThat(repo.lastReport?.messageId).isEqualTo("r1")
        assertThat(repo.lastReport?.reason).isEqualTo(ReportReason.SPAM)
        assertThat(vm.uiState.value.reportTarget).isNull()
    }

    @Test
    fun `fetchMetadata delegates for the current message`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository().apply {
            fetchResult = ApiResult.Success(sampleMessage(id = "m1"))
            metadataResult = ApiResult.Success(sampleMessage(id = "m1"))
        }
        repo.emitMessage(sampleMessage(id = "m1"))
        val vm = MessageDetailViewModel(repo, handle("m1"))
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onFetchMetadata()
        advanceUntilIdle()

        assertThat(repo.metadataFetchedIds).containsExactly("m1")
    }
}
