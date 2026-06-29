package com.interlinedlist.android.feature.auth.ui

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.core.model.CustomerStatus
import com.interlinedlist.android.core.model.User
import com.interlinedlist.android.feature.auth.data.AuthRepository
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
class LoginViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private class FakeAuthRepository(var result: ApiResult<User>) : AuthRepository {
        var loginCount = 0
        override fun isLoggedIn() = false
        override suspend fun login(email: String, password: String): ApiResult<User> {
            loginCount++
            return result
        }
        override suspend fun logout() = Unit
    }

    private val sampleUser = User(
        id = "1", username = "messenger", displayName = "Messenger",
        email = null, avatarUrl = null, bio = null,
        emailVerified = true, customerStatus = CustomerStatus.SUBSCRIBER,
    )

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `blank credentials show validation error and skip the repository`() = runTest(dispatcher) {
        val repo = FakeAuthRepository(ApiResult.Success(sampleUser))
        val vm = LoginViewModel(repo)

        var succeeded = false
        vm.login { succeeded = true }
        advanceUntilIdle()

        assertThat(repo.loginCount).isEqualTo(0)
        assertThat(succeeded).isFalse()
        assertThat(vm.uiState.value.errorMessage).isEqualTo("Enter your email and password.")
    }

    @Test
    fun `successful login invokes onSuccess and clears loading`() = runTest(dispatcher) {
        val repo = FakeAuthRepository(ApiResult.Success(sampleUser))
        val vm = LoginViewModel(repo)
        vm.onEmailChange("you@example.com")
        vm.onPasswordChange("secret")

        var succeeded = false
        vm.login { succeeded = true }
        advanceUntilIdle()

        assertThat(repo.loginCount).isEqualTo(1)
        assertThat(succeeded).isTrue()
        assertThat(vm.uiState.value.isLoading).isFalse()
        assertThat(vm.uiState.value.errorMessage).isNull()
    }

    @Test
    fun `failed login surfaces a mapped error and does not navigate`() = runTest(dispatcher) {
        val repo = FakeAuthRepository(ApiResult.Failure(AppError.Unauthorized("Invalid credentials")))
        val vm = LoginViewModel(repo)
        vm.onEmailChange("you@example.com")
        vm.onPasswordChange("wrong")

        var succeeded = false
        vm.login { succeeded = true }
        advanceUntilIdle()

        assertThat(succeeded).isFalse()
        assertThat(vm.uiState.value.isLoading).isFalse()
        assertThat(vm.uiState.value.errorMessage).isEqualTo("Invalid credentials")
    }
}
