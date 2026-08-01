package com.interlinedlist.android.feature.profile.ui

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.profile.ui.follow.FollowRequestsViewModel
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
class FollowRequestsViewModelTest {

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
    fun `loads pending requests`() = runTest(dispatcher) {
        repo.followRequestsResult = ApiResult.Success(
            listOf(testFollowUser(id = "1", username = "eve"), testFollowUser(id = "2", username = "frank")),
        )

        val vm = FollowRequestsViewModel(repo)
        advanceUntilIdle()

        assertThat(repo.followRequestsCount).isEqualTo(1)
        assertThat(vm.uiState.value.requests.map { it.username }).containsExactly("eve", "frank").inOrder()
        assertThat(vm.uiState.value.isLoading).isFalse()
    }

    @Test
    fun `no requests flags isEmpty`() = runTest(dispatcher) {
        repo.followRequestsResult = ApiResult.Success(emptyList())

        val vm = FollowRequestsViewModel(repo)
        advanceUntilIdle()

        assertThat(vm.uiState.value.isEmpty).isTrue()
    }

    @Test
    fun `approve removes the request row and calls the repository`() = runTest(dispatcher) {
        repo.followRequestsResult = ApiResult.Success(
            listOf(testFollowUser(id = "1", username = "eve"), testFollowUser(id = "2", username = "frank")),
        )
        val vm = FollowRequestsViewModel(repo)
        advanceUntilIdle()

        vm.approve("1")
        advanceUntilIdle()

        assertThat(repo.approvedUserId).isEqualTo("1")
        assertThat(vm.uiState.value.requests.map { it.id }).containsExactly("2")
        assertThat(vm.uiState.value.pendingActionIds).isEmpty()
    }

    @Test
    fun `reject removes the request row and calls the repository`() = runTest(dispatcher) {
        repo.followRequestsResult = ApiResult.Success(listOf(testFollowUser(id = "1", username = "eve")))
        val vm = FollowRequestsViewModel(repo)
        advanceUntilIdle()

        vm.reject("1")
        advanceUntilIdle()

        assertThat(repo.rejectedUserId).isEqualTo("1")
        assertThat(vm.uiState.value.requests).isEmpty()
    }

    @Test
    fun `a failed action keeps the row and surfaces an error`() = runTest(dispatcher) {
        repo.followRequestsResult = ApiResult.Success(listOf(testFollowUser(id = "1", username = "eve")))
        repo.approveResult = ApiResult.Failure(AppError.Server("boom"))
        val vm = FollowRequestsViewModel(repo)
        advanceUntilIdle()

        vm.approve("1")
        advanceUntilIdle()

        assertThat(vm.uiState.value.requests.map { it.id }).containsExactly("1")
        assertThat(vm.uiState.value.errorMessage).isNotNull()
        assertThat(vm.uiState.value.pendingActionIds).isEmpty()
    }

    @Test
    fun `a load failure surfaces a mapped error`() = runTest(dispatcher) {
        repo.followRequestsResult = ApiResult.Failure(AppError.Network("offline"))

        val vm = FollowRequestsViewModel(repo)
        advanceUntilIdle()

        assertThat(vm.uiState.value.errorMessage).isEqualTo("No connection. Check your network and try again.")
    }
}
