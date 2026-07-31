package com.interlinedlist.android.feature.documents.ui.share

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.documents.domain.ShareLink
import com.interlinedlist.android.feature.documents.domain.ShareRole
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
class DocumentShareViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private fun link(token: String, role: ShareRole = ShareRole.VIEW) =
        ShareLink("id-$token", token, role, null, null, null)

    private fun viewModel(repo: FakeDocumentsRepository) =
        DocumentShareViewModel(repo, SavedStateHandle(mapOf(SHARE_DOCUMENT_ID_ARG to "D1")))

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `loads existing share links on init`() = runTest(dispatcher) {
        val repo = FakeDocumentsRepository().apply {
            shareLinksResult = ApiResult.Success(listOf(link("a"), link("b", ShareRole.EDIT)))
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        assertThat(vm.uiState.value.isLoading).isFalse()
        assertThat(vm.uiState.value.activeLinks.map { it.token }).containsExactly("a", "b").inOrder()
    }

    @Test
    fun `createLink optimistically appends the created link with the chosen role`() = runTest(dispatcher) {
        val repo = FakeDocumentsRepository().apply {
            shareLinksResult = ApiResult.Success(emptyList())
            createShareLinkResult = ApiResult.Success(link("fresh", ShareRole.ADMIN))
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.selectRole(ShareRole.ADMIN)
        vm.createLink()
        advanceUntilIdle()

        assertThat(repo.createShareLinkCount).isEqualTo(1)
        assertThat(repo.lastCreatedShareRole).isEqualTo(ShareRole.ADMIN)
        assertThat(vm.uiState.value.activeLinks.map { it.token }).containsExactly("fresh")
    }

    @Test
    fun `createLink failure surfaces an error and adds nothing`() = runTest(dispatcher) {
        val repo = FakeDocumentsRepository().apply {
            shareLinksResult = ApiResult.Success(emptyList())
            createShareLinkResult = ApiResult.Failure(AppError.Network("offline"))
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.createLink()
        advanceUntilIdle()

        assertThat(vm.uiState.value.activeLinks).isEmpty()
        assertThat(vm.uiState.value.errorMessage).isNotNull()
    }

    @Test
    fun `revokeLink optimistically removes the link on success`() = runTest(dispatcher) {
        val repo = FakeDocumentsRepository().apply {
            shareLinksResult = ApiResult.Success(listOf(link("keep"), link("drop")))
            revokeShareLinkResult = ApiResult.Success(Unit)
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.revokeLink(link("drop"))
        // Removed immediately, before the network resolves.
        assertThat(vm.uiState.value.activeLinks.map { it.token }).containsExactly("keep")

        advanceUntilIdle()
        assertThat(repo.lastRevokedToken).isEqualTo("drop")
        assertThat(vm.uiState.value.activeLinks.map { it.token }).containsExactly("keep")
    }

    @Test
    fun `revokeLink rolls back and shows an error on failure`() = runTest(dispatcher) {
        val repo = FakeDocumentsRepository().apply {
            shareLinksResult = ApiResult.Success(listOf(link("keep"), link("drop")))
            revokeShareLinkResult = ApiResult.Failure(AppError.Server("boom"))
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.revokeLink(link("drop"))
        advanceUntilIdle()

        assertThat(vm.uiState.value.activeLinks.map { it.token }).containsExactly("keep", "drop").inOrder()
        assertThat(vm.uiState.value.errorMessage).isNotNull()
    }
}
