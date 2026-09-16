package com.interlinedlist.android.feature.directmessages.ui.newmessage

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.directmessages.data.Recipient
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
class NewMessageViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val adron = Recipient("u1", "adron", "Adron Hall", null)
    private val blake = Recipient("u2", "blake", "Blake", null)

    @Test
    fun `loads recipients on init`() = runTest(dispatcher) {
        val repo = FakeDirectMessagesRepository()
        repo.recipientsResult = ApiResult.Success(listOf(adron, blake))

        val vm = NewMessageViewModel(repo)
        advanceUntilIdle()

        assertThat(vm.uiState.value.recipients).hasSize(2)
        assertThat(vm.uiState.value.isLoading).isFalse()
    }

    @Test
    fun `query filters recipients by username and display name`() = runTest(dispatcher) {
        val repo = FakeDirectMessagesRepository()
        repo.recipientsResult = ApiResult.Success(listOf(adron, blake))
        val vm = NewMessageViewModel(repo)
        advanceUntilIdle()

        vm.onQueryChange("adr")
        assertThat(vm.uiState.value.filtered.map { it.username }).containsExactly("adron")

        vm.onQueryChange("blake")
        assertThat(vm.uiState.value.filtered.map { it.username }).containsExactly("blake")

        vm.onQueryChange("")
        assertThat(vm.uiState.value.filtered).hasSize(2)
    }

    @Test
    fun `failure surfaces an error`() = runTest(dispatcher) {
        val repo = FakeDirectMessagesRepository()
        repo.recipientsResult = ApiResult.Failure(AppError.Network("offline"))

        val vm = NewMessageViewModel(repo)
        advanceUntilIdle()

        assertThat(vm.uiState.value.isLoading).isFalse()
        assertThat(vm.uiState.value.errorMessage).isNotNull()
    }

    // ---- loading / loaded-but-empty / error are three distinct states ----

    @Test
    fun `content is Loading while the first load is in flight`() = runTest(dispatcher) {
        val repo = FakeDirectMessagesRepository()
        repo.recipientsResult = ApiResult.Success(listOf(adron))

        val vm = NewMessageViewModel(repo)

        assertThat(vm.uiState.value.content).isEqualTo(NewMessageContent.Loading)
    }

    @Test
    fun `content is NoRecipients when the server returns an empty set`() = runTest(dispatcher) {
        val repo = FakeDirectMessagesRepository()
        repo.recipientsResult = ApiResult.Success(emptyList())

        val vm = NewMessageViewModel(repo)
        advanceUntilIdle()

        assertThat(vm.uiState.value.content).isEqualTo(NewMessageContent.NoRecipients)
        assertThat(vm.uiState.value.errorMessage).isNull()
    }

    @Test
    fun `content is Recipients when the server returns people`() = runTest(dispatcher) {
        val repo = FakeDirectMessagesRepository()
        repo.recipientsResult = ApiResult.Success(listOf(adron, blake))

        val vm = NewMessageViewModel(repo)
        advanceUntilIdle()

        assertThat(vm.uiState.value.content).isEqualTo(NewMessageContent.Recipients)
    }

    @Test
    fun `content is Error on failure, never the empty explanation`() = runTest(dispatcher) {
        val repo = FakeDirectMessagesRepository()
        repo.recipientsResult = ApiResult.Failure(AppError.Network("offline"))

        val vm = NewMessageViewModel(repo)
        advanceUntilIdle()

        assertThat(vm.uiState.value.content).isEqualTo(NewMessageContent.Error)
        assertThat(vm.uiState.value.content).isNotEqualTo(NewMessageContent.NoRecipients)
    }

    @Test
    fun `a query that matches no one is NoMatches, not the rule explanation`() =
        runTest(dispatcher) {
            val repo = FakeDirectMessagesRepository()
            repo.recipientsResult = ApiResult.Success(listOf(adron, blake))
            val vm = NewMessageViewModel(repo)
            advanceUntilIdle()

            vm.onQueryChange("nobody-by-that-name")

            assertThat(vm.uiState.value.content).isEqualTo(NewMessageContent.NoMatches)
        }

    @Test
    fun `retry reloads and clears the previous error`() = runTest(dispatcher) {
        val repo = FakeDirectMessagesRepository()
        repo.recipientsResult = ApiResult.Failure(AppError.Network("offline"))
        val vm = NewMessageViewModel(repo)
        advanceUntilIdle()
        assertThat(vm.uiState.value.content).isEqualTo(NewMessageContent.Error)

        repo.recipientsResult = ApiResult.Success(listOf(adron))
        vm.load()
        advanceUntilIdle()

        assertThat(repo.recipientsCount).isEqualTo(2)
        assertThat(vm.uiState.value.errorMessage).isNull()
        assertThat(vm.uiState.value.content).isEqualTo(NewMessageContent.Recipients)
    }
}
