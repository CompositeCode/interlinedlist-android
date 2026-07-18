package com.interlinedlist.android.feature.profile.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.profile.ui.profile.ProfileViewModel
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
class ProfileViewModelTest {

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
    fun `emits the current user from the room flow and clears loading`() = runTest(dispatcher) {
        val user = testUser(username = "adron", displayName = "Adron Hall")
        repo.refreshCurrentUserResult = ApiResult.Success(user)
        repo.currentUserFlow.value = user

        val vm = ProfileViewModel(repo)
        advanceUntilIdle()

        assertThat(vm.uiState.value.user?.username).isEqualTo("adron")
        assertThat(vm.uiState.value.isLoading).isFalse()
    }

    @Test
    fun `refresh failure surfaces a mapped error`() = runTest(dispatcher) {
        repo.refreshCurrentUserResult = ApiResult.Failure(AppError.Network("offline"))

        val vm = ProfileViewModel(repo)
        advanceUntilIdle()

        assertThat(vm.uiState.value.errorMessage).isEqualTo("No connection. Check your network and try again.")
        assertThat(vm.uiState.value.isLoading).isFalse()
    }

    @Test
    fun `cached user still shows even when the refresh fails offline`() = runTest(dispatcher) {
        val cached = testUser(username = "adron")
        repo.currentUserFlow.value = cached
        repo.refreshCurrentUserResult = ApiResult.Failure(AppError.Network("offline"))

        val vm = ProfileViewModel(repo)
        advanceUntilIdle()

        assertThat(vm.uiState.value.user?.username).isEqualTo("adron")
        assertThat(vm.uiState.value.errorMessage).isNotNull()
    }

    @Test
    fun `state updates via Turbine when the cached user changes`() = runTest(dispatcher) {
        repo.refreshCurrentUserResult = ApiResult.Success(testUser())

        val vm = ProfileViewModel(repo)
        advanceUntilIdle()

        vm.uiState.test {
            assertThat(awaitItem().user?.displayName).isEqualTo("Adron Hall")
            repo.currentUserFlow.value = testUser(displayName = "Renamed")
            assertThat(awaitItem().user?.displayName).isEqualTo("Renamed")
        }
    }
}
