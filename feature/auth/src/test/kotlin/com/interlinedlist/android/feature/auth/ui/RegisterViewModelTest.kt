package com.interlinedlist.android.feature.auth.ui

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.core.model.CustomerStatus
import com.interlinedlist.android.core.model.User
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
class RegisterViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun filledVm(repo: FakeAuthRepository): RegisterViewModel =
        RegisterViewModel(repo).apply {
            onDisplayNameChange("New Bie")
            onUsernameChange("newbie")
            onEmailChange("new@example.com")
            onPasswordChange("s3cret!!")
            onConfirmPasswordChange("s3cret!!")
        }

    @Test
    fun `mismatched passwords block submit and show a field error`() = runTest(dispatcher) {
        val repo = FakeAuthRepository()
        val vm = filledVm(repo)
        vm.onConfirmPasswordChange("different")

        assertThat(vm.uiState.value.canSubmit).isFalse()
        assertThat(vm.uiState.value.passwordsMatch).isFalse()

        vm.register(onRegistered = {})
        advanceUntilIdle()

        assertThat(repo.registerCount).isEqualTo(0)
    }

    @Test
    fun `blank required fields block submit`() = runTest(dispatcher) {
        val vm = RegisterViewModel(FakeAuthRepository())
        vm.onEmailChange("only@example.com")

        assertThat(vm.uiState.value.canSubmit).isFalse()
    }

    @Test
    fun `successful register forwards the trimmed fields and navigates`() = runTest(dispatcher) {
        val repo = FakeAuthRepository(registerResult = ApiResult.Success(sampleUser))
        val vm = filledVm(repo)
        vm.onEmailChange("  new@example.com  ")

        var registered = false
        vm.register(onRegistered = { registered = true })
        advanceUntilIdle()

        assertThat(registered).isTrue()
        assertThat(repo.registerCount).isEqualTo(1)
        assertThat(repo.lastRegister).isEqualTo(
            FakeAuthRepository.RegisterArgs(
                email = "new@example.com",
                username = "newbie",
                password = "s3cret!!",
                displayName = "New Bie",
            ),
        )
        assertThat(vm.uiState.value.isLoading).isFalse()
    }

    @Test
    fun `unverified user surfaces the email hint after registering`() = runTest(dispatcher) {
        val unverified = User(
            id = "9", username = "newbie", displayName = null, email = "new@example.com",
            avatarUrl = null, bio = null, emailVerified = false,
            customerStatus = CustomerStatus.FREE,
        )
        val repo = FakeAuthRepository(registerResult = ApiResult.Success(unverified))
        val vm = filledVm(repo)

        vm.register(onRegistered = {})
        advanceUntilIdle()

        assertThat(vm.uiState.value.showVerifyEmailHint).isTrue()
    }

    @Test
    fun `email already taken maps to an error and does not navigate`() = runTest(dispatcher) {
        val repo = FakeAuthRepository(
            registerResult = ApiResult.Failure(AppError.Conflict("Email already in use")),
        )
        val vm = filledVm(repo)

        var registered = false
        vm.register(onRegistered = { registered = true })
        advanceUntilIdle()

        assertThat(registered).isFalse()
        assertThat(vm.uiState.value.isLoading).isFalse()
        assertThat(vm.uiState.value.errorMessage).isEqualTo("Email already in use")
    }
}
