package com.interlinedlist.android.feature.profile.ui

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.profile.ui.profile.PROFILE_USERNAME_ARG
import com.interlinedlist.android.feature.profile.ui.profile.UserProfileViewModel
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
class UserProfileViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeProfileRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeProfileRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(username: String) =
        UserProfileViewModel(repo, SavedStateHandle(mapOf(PROFILE_USERNAME_ARG to username)))

    @Test
    fun `loads the user named by the nav arg`() = runTest(dispatcher) {
        val ada = testUser(id = "u2", username = "ada", displayName = "Ada Lovelace", isCurrentUser = false)
        repo.refreshUserResult = ApiResult.Success(ada)
        repo.userFlow.value = ada

        val vm = viewModel("ada")
        advanceUntilIdle()

        assertThat(vm.uiState.value.user?.username).isEqualTo("ada")
        assertThat(vm.uiState.value.user?.isCurrentUser).isFalse()
        assertThat(vm.uiState.value.isLoading).isFalse()
    }

    @Test
    fun `a missing user surfaces a not-found message`() = runTest(dispatcher) {
        repo.refreshUserResult = ApiResult.Failure(AppError.NotFound("No such user"))

        val vm = viewModel("ghost")
        advanceUntilIdle()

        assertThat(vm.uiState.value.errorMessage).isEqualTo("No such user")
        assertThat(vm.uiState.value.user).isNull()
    }

    @Test
    fun `missing nav arg fails fast`() {
        try {
            UserProfileViewModel(repo, SavedStateHandle())
            throw AssertionError("Expected IllegalStateException for missing nav arg")
        } catch (e: IllegalStateException) {
            assertThat(e).hasMessageThat().contains(PROFILE_USERNAME_ARG)
        }
    }
}
