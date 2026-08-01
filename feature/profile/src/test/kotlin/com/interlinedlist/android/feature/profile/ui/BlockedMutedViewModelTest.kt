package com.interlinedlist.android.feature.profile.ui

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.profile.ui.account.BlockedMutedViewModel
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
class BlockedMutedViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeProfileRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeProfileRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `loads both blocked and muted lists`() = runTest(dispatcher) {
        repo.blockedUsersResult = ApiResult.Success(
            listOf(testModeratedUser(id = "b1", username = "ada"), testModeratedUser(id = "b2", username = "grace")),
        )
        repo.mutedUsersResult = ApiResult.Success(listOf(testModeratedUser(id = "m1", username = "noisy")))

        val vm = BlockedMutedViewModel(repo)
        advanceUntilIdle()

        assertThat(repo.blockedUsersCount).isEqualTo(1)
        assertThat(repo.mutedUsersCount).isEqualTo(1)
        assertThat(vm.uiState.value.blocked.map { it.username }).containsExactly("ada", "grace").inOrder()
        assertThat(vm.uiState.value.muted.map { it.username }).containsExactly("noisy")
        assertThat(vm.uiState.value.isLoading).isFalse()
    }

    @Test
    fun `empty lists flag isEmpty`() = runTest(dispatcher) {
        repo.blockedUsersResult = ApiResult.Success(emptyList())
        repo.mutedUsersResult = ApiResult.Success(emptyList())

        val vm = BlockedMutedViewModel(repo)
        advanceUntilIdle()

        assertThat(vm.uiState.value.isEmpty).isTrue()
    }

    @Test
    fun `unblock removes the row optimistically and calls the repository`() = runTest(dispatcher) {
        repo.blockedUsersResult = ApiResult.Success(
            listOf(testModeratedUser(username = "ada"), testModeratedUser(id = "b2", username = "grace")),
        )
        val vm = BlockedMutedViewModel(repo)
        advanceUntilIdle()

        vm.unblock("ada")
        advanceUntilIdle()

        assertThat(repo.unblockedUsername).isEqualTo("ada")
        assertThat(vm.uiState.value.blocked.map { it.username }).containsExactly("grace")
        assertThat(vm.uiState.value.pendingIds).isEmpty()
    }

    @Test
    fun `unblock rolls the row back and surfaces an error on failure`() = runTest(dispatcher) {
        repo.blockedUsersResult = ApiResult.Success(listOf(testModeratedUser(username = "ada")))
        repo.unblockResult = ApiResult.Failure(AppError.Server("boom"))
        val vm = BlockedMutedViewModel(repo)
        advanceUntilIdle()

        vm.unblock("ada")
        advanceUntilIdle()

        assertThat(vm.uiState.value.blocked.map { it.username }).containsExactly("ada")
        assertThat(vm.uiState.value.errorMessage).isNotNull()
        assertThat(vm.uiState.value.pendingIds).isEmpty()
    }

    @Test
    fun `unmute removes the row optimistically`() = runTest(dispatcher) {
        repo.mutedUsersResult = ApiResult.Success(
            listOf(testModeratedUser(id = "m1", username = "noisy"), testModeratedUser(id = "m2", username = "loud")),
        )
        val vm = BlockedMutedViewModel(repo)
        advanceUntilIdle()

        vm.unmute("noisy")
        advanceUntilIdle()

        assertThat(repo.unmutedUsername).isEqualTo("noisy")
        assertThat(vm.uiState.value.muted.map { it.username }).containsExactly("loud")
    }

    @Test
    fun `a load failure surfaces a mapped error`() = runTest(dispatcher) {
        repo.blockedUsersResult = ApiResult.Failure(AppError.Network("offline"))

        val vm = BlockedMutedViewModel(repo)
        advanceUntilIdle()

        assertThat(vm.uiState.value.errorMessage).isEqualTo("No connection. Check your network and try again.")
    }
}
