package com.interlinedlist.android.feature.documents.ui.share

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.documents.domain.ShareRole
import com.interlinedlist.android.feature.documents.domain.SharedDocument
import com.interlinedlist.android.feature.documents.ui.FakeDocumentsRepository
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
class SharedDocumentViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private fun shared(role: ShareRole) =
        SharedDocument("tok", "D5", "Notes", "# Body", "Grace", role)

    private fun viewModel(repo: FakeDocumentsRepository) =
        SharedDocumentViewModel(repo, SavedStateHandle(mapOf(SHARED_DOCUMENT_TOKEN_ARG to "tok")))

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `resolves the token into a preview on init`() = runTest(dispatcher) {
        val repo = FakeDocumentsRepository().apply {
            resolveSharedResult = ApiResult.Success(shared(ShareRole.EDIT))
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        assertThat(repo.lastResolvedToken).isEqualTo("tok")
        assertThat(vm.uiState.value.document?.documentId).isEqualTo("D5")
        assertThat(vm.uiState.value.canClaim).isTrue()
    }

    @Test
    fun `a view-only link cannot be claimed`() = runTest(dispatcher) {
        val repo = FakeDocumentsRepository().apply {
            resolveSharedResult = ApiResult.Success(shared(ShareRole.VIEW))
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        assertThat(vm.uiState.value.canClaim).isFalse()
        vm.claim()
        advanceUntilIdle()
        assertThat(repo.claimSharedCount).isEqualTo(0)
    }

    @Test
    fun `claim success flips claimed`() = runTest(dispatcher) {
        val repo = FakeDocumentsRepository().apply {
            resolveSharedResult = ApiResult.Success(shared(ShareRole.ADMIN))
            claimSharedResult = ApiResult.Success(Unit)
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.claim()
        advanceUntilIdle()

        assertThat(repo.claimSharedCount).isEqualTo(1)
        assertThat(repo.lastClaimedToken).isEqualTo("tok")
        assertThat(vm.uiState.value.claimed).isTrue()
        assertThat(vm.uiState.value.canClaim).isFalse()
    }

    @Test
    fun `claim failure surfaces an error and stays unclaimed`() = runTest(dispatcher) {
        val repo = FakeDocumentsRepository().apply {
            resolveSharedResult = ApiResult.Success(shared(ShareRole.EDIT))
            claimSharedResult = ApiResult.Failure(AppError.Forbidden("nope"))
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.claim()
        advanceUntilIdle()

        assertThat(vm.uiState.value.claimed).isFalse()
        assertThat(vm.uiState.value.errorMessage).isNotNull()
    }

    @Test
    fun `resolve failure surfaces an error`() = runTest(dispatcher) {
        val repo = FakeDocumentsRepository().apply {
            resolveSharedResult = ApiResult.Failure(AppError.NotFound("gone"))
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        assertThat(vm.uiState.value.document).isNull()
        assertThat(vm.uiState.value.errorMessage).isNotNull()
    }
}
