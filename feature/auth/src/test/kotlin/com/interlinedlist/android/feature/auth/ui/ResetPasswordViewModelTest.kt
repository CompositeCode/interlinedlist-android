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
class ResetPasswordViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `token from the deep link seeds the state`() = runTest(dispatcher) {
        val vm = ResetPasswordViewModel(FakeAuthRepository(), token = "deep-link-token")
        assertThat(vm.uiState.value.token).isEqualTo("deep-link-token")
    }

    @Test
    fun `mismatched passwords block submit`() = runTest(dispatcher) {
        val repo = FakeAuthRepository()
        val vm = ResetPasswordViewModel(repo, token = "t")
        vm.onPasswordChange("brandN3w!")
        vm.onConfirmPasswordChange("nope")

        assertThat(vm.uiState.value.canSubmit).isFalse()

        vm.submit(onReset = {})
        advanceUntilIdle()

        assertThat(repo.lastReset).isNull()
    }

    @Test
    fun `success forwards token and new password then navigates back to login`() = runTest(dispatcher) {
        val repo = FakeAuthRepository(resetResult = ApiResult.Success(Unit))
        val vm = ResetPasswordViewModel(repo, token = "reset-tok")
        vm.onPasswordChange("brandN3w!")
        vm.onConfirmPasswordChange("brandN3w!")

        var reset = false
        vm.submit(onReset = { reset = true })
        advanceUntilIdle()

        assertThat(reset).isTrue()
        assertThat(repo.lastReset).isEqualTo(
            FakeAuthRepository.ResetArgs(token = "reset-tok", newPassword = "brandN3w!"),
        )
    }

    @Test
    fun `invalid token maps to an error and stays on the form`() = runTest(dispatcher) {
        val repo = FakeAuthRepository(
            resetResult = ApiResult.Failure(AppError.Unknown("Invalid or expired token")),
        )
        val vm = ResetPasswordViewModel(repo, token = "bad")
        vm.onPasswordChange("brandN3w!")
        vm.onConfirmPasswordChange("brandN3w!")

        var reset = false
        vm.submit(onReset = { reset = true })
        advanceUntilIdle()

        assertThat(reset).isFalse()
        assertThat(vm.uiState.value.errorMessage).isEqualTo("Invalid or expired token")
    }
}
