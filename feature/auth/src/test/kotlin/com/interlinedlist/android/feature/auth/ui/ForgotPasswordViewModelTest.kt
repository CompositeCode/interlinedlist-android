package com.interlinedlist.android.feature.auth.ui

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
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
class ForgotPasswordViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `submit success shows the check-your-email confirmation`() = runTest(dispatcher) {
        val repo = FakeAuthRepository(forgotResult = ApiResult.Success(Unit))
        val vm = ForgotPasswordViewModel(repo)
        vm.onEmailChange("  me@example.com ")

        vm.submit()
        advanceUntilIdle()

        assertThat(vm.uiState.value.emailSent).isTrue()
        assertThat(vm.uiState.value.errorMessage).isNull()
        assertThat(repo.lastForgotEmail).isEqualTo("me@example.com")
    }

    @Test
    fun `blank email blocks submit`() = runTest(dispatcher) {
        val repo = FakeAuthRepository()
        val vm = ForgotPasswordViewModel(repo)

        assertThat(vm.uiState.value.canSubmit).isFalse()

        vm.submit()
        advanceUntilIdle()

        assertThat(repo.lastForgotEmail).isNull()
    }

    @Test
    fun `failure surfaces a mapped error and stays on the form`() = runTest(dispatcher) {
        val repo = FakeAuthRepository(
            forgotResult = ApiResult.Failure(AppError.RateLimited(null)),
        )
        val vm = ForgotPasswordViewModel(repo)
        vm.onEmailChange("me@example.com")

        vm.submit()
        advanceUntilIdle()

        assertThat(vm.uiState.value.emailSent).isFalse()
        assertThat(vm.uiState.value.errorMessage).isNotNull()
    }
}
