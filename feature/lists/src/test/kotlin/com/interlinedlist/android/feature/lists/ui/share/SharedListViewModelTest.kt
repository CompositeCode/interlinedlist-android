package com.interlinedlist.android.feature.lists.ui.share

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.lists.FakeListsRepository
import com.interlinedlist.android.feature.lists.domain.ShareRole
import com.interlinedlist.android.feature.lists.domain.SharedList
import com.interlinedlist.android.feature.lists.domain.SharedListResolution
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
class SharedListViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private fun resolution(role: ShareRole) =
        SharedListResolution("tok", "L5", "Reading", "Books", "Grace", role, emptyList())

    private fun sharedVm(repo: FakeListsRepository) =
        SharedListViewModel(repo, SavedStateHandle(mapOf(SHARED_TOKEN_ARG to "tok")))

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `resolves the token into a preview on init`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply {
            resolveSharedResult = ApiResult.Success(resolution(ShareRole.EDIT))
        }
        val vm = sharedVm(repo)
        advanceUntilIdle()

        assertThat(repo.lastResolvedToken).isEqualTo("tok")
        assertThat(vm.uiState.value.resolution?.listId).isEqualTo("L5")
        assertThat(vm.uiState.value.canClaim).isTrue()
    }

    @Test
    fun `a view-only link cannot be claimed`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply {
            resolveSharedResult = ApiResult.Success(resolution(ShareRole.VIEW))
        }
        val vm = sharedVm(repo)
        advanceUntilIdle()

        assertThat(vm.uiState.value.canClaim).isFalse()
        vm.claim()
        advanceUntilIdle()
        assertThat(repo.claimSharedCount).isEqualTo(0)
    }

    @Test
    fun `claim success flips claimed`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply {
            resolveSharedResult = ApiResult.Success(resolution(ShareRole.ADMIN))
            claimSharedResult = ApiResult.Success(Unit)
        }
        val vm = sharedVm(repo)
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
        val repo = FakeListsRepository().apply {
            resolveSharedResult = ApiResult.Success(resolution(ShareRole.EDIT))
            claimSharedResult = ApiResult.Failure(AppError.Forbidden("nope"))
        }
        val vm = sharedVm(repo)
        advanceUntilIdle()

        vm.claim()
        advanceUntilIdle()

        assertThat(vm.uiState.value.claimed).isFalse()
        assertThat(vm.uiState.value.errorMessage).isNotNull()
    }

    @Test
    fun `resolve failure surfaces an error`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply {
            resolveSharedResult = ApiResult.Failure(AppError.NotFound("gone"))
        }
        val vm = sharedVm(repo)
        advanceUntilIdle()

        assertThat(vm.uiState.value.resolution).isNull()
        assertThat(vm.uiState.value.errorMessage).isNotNull()
    }

    @Test
    fun `shared-with-me loads lists with owner and role`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply {
            sharedWithMeResult = ApiResult.Success(
                listOf(
                    SharedList("w1", "Shows", null, "Adron Hall", ShareRole.EDIT, true),
                    SharedList("w2", "Videos", null, "Adron Hall", ShareRole.VIEW, true),
                ),
            )
        }
        val vm = SharedWithMeViewModel(repo)
        advanceUntilIdle()

        assertThat(vm.uiState.value.lists.map { it.id }).containsExactly("w1", "w2").inOrder()
        assertThat(vm.uiState.value.lists[0].role).isEqualTo(ShareRole.EDIT)
        assertThat(vm.uiState.value.isLoading).isFalse()
    }
}
