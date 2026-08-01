package com.interlinedlist.android.feature.profile.ui

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.profile.ui.account.SessionsViewModel
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
class SessionsViewModelTest {

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
    fun `loads sessions`() = runTest(dispatcher) {
        repo.sessionsResult = ApiResult.Success(
            listOf(
                testSession(id = "s1", isCurrent = true),
                testSession(id = "s2", isCurrent = false),
            ),
        )

        val vm = SessionsViewModel(repo)
        advanceUntilIdle()

        assertThat(repo.sessionsCount).isEqualTo(1)
        assertThat(vm.uiState.value.sessions.map { it.id }).containsExactly("s1", "s2").inOrder()
        assertThat(vm.uiState.value.isLoading).isFalse()
    }

    @Test
    fun `no sessions flags isEmpty`() = runTest(dispatcher) {
        repo.sessionsResult = ApiResult.Success(emptyList())

        val vm = SessionsViewModel(repo)
        advanceUntilIdle()

        assertThat(vm.uiState.value.isEmpty).isTrue()
    }

    @Test
    fun `revoke removes the row optimistically and calls the repository`() = runTest(dispatcher) {
        repo.sessionsResult = ApiResult.Success(
            listOf(
                testSession(id = "s1", isCurrent = true),
                testSession(id = "s2", isCurrent = false),
            ),
        )
        val vm = SessionsViewModel(repo)
        advanceUntilIdle()

        vm.revoke("s2")
        advanceUntilIdle()

        assertThat(repo.revokedSessionId).isEqualTo("s2")
        assertThat(vm.uiState.value.sessions.map { it.id }).containsExactly("s1")
        assertThat(vm.uiState.value.pendingRevokeIds).isEmpty()
    }

    @Test
    fun `revoke rolls the row back and surfaces an error on failure`() = runTest(dispatcher) {
        repo.sessionsResult = ApiResult.Success(
            listOf(
                testSession(id = "s1", isCurrent = true),
                testSession(id = "s2", isCurrent = false),
            ),
        )
        repo.revokeSessionResult = ApiResult.Failure(AppError.Server("boom"))
        val vm = SessionsViewModel(repo)
        advanceUntilIdle()

        vm.revoke("s2")
        advanceUntilIdle()

        assertThat(vm.uiState.value.sessions.map { it.id }).containsExactly("s1", "s2")
        assertThat(vm.uiState.value.errorMessage).isNotNull()
        assertThat(vm.uiState.value.pendingRevokeIds).isEmpty()
    }

    @Test
    fun `revoke ignores the current device`() = runTest(dispatcher) {
        repo.sessionsResult = ApiResult.Success(listOf(testSession(id = "s1", isCurrent = true)))
        val vm = SessionsViewModel(repo)
        advanceUntilIdle()

        vm.revoke("s1")
        advanceUntilIdle()

        assertThat(repo.revokedSessionId).isNull()
        assertThat(vm.uiState.value.sessions.map { it.id }).containsExactly("s1")
    }

    @Test
    fun `a load failure surfaces a mapped error`() = runTest(dispatcher) {
        repo.sessionsResult = ApiResult.Failure(AppError.Network("offline"))

        val vm = SessionsViewModel(repo)
        advanceUntilIdle()

        assertThat(vm.uiState.value.errorMessage).isEqualTo("No connection. Check your network and try again.")
    }
}
