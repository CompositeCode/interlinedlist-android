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
class VerifyEmailViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a deep-link token verifies automatically and reports success`() = runTest(dispatcher) {
        val repo = FakeAuthRepository(verifyResult = ApiResult.Success(Unit))
        val vm = VerifyEmailViewModel(repo, token = "verify-tok")
        advanceUntilIdle()

        assertThat(repo.lastVerifyToken).isEqualTo("verify-tok")
        assertThat(vm.uiState.value.status).isEqualTo(VerifyEmailStatus.VERIFIED)
    }

    @Test
    fun `an invalid deep-link token reports failure`() = runTest(dispatcher) {
        val repo = FakeAuthRepository(
            verifyResult = ApiResult.Failure(AppError.Unknown("Verification link has expired")),
        )
        val vm = VerifyEmailViewModel(repo, token = "stale")
        advanceUntilIdle()

        assertThat(vm.uiState.value.status).isEqualTo(VerifyEmailStatus.FAILED)
        assertThat(vm.uiState.value.message).isEqualTo("Verification link has expired")
    }

    @Test
    fun `with no token the screen only offers a resend action`() = runTest(dispatcher) {
        val repo = FakeAuthRepository()
        val vm = VerifyEmailViewModel(repo, token = null)
        advanceUntilIdle()

        assertThat(repo.lastVerifyToken).isNull()
        assertThat(vm.uiState.value.status).isEqualTo(VerifyEmailStatus.IDLE)
    }

    @Test
    fun `resend requests a fresh verification email`() = runTest(dispatcher) {
        val repo = FakeAuthRepository(resendResult = ApiResult.Success(Unit))
        val vm = VerifyEmailViewModel(repo, token = null)

        vm.resend()
        advanceUntilIdle()

        assertThat(repo.resendCount).isEqualTo(1)
        assertThat(vm.uiState.value.resendConfirmed).isTrue()
    }

    @Test
    fun `resend failure surfaces a mapped error`() = runTest(dispatcher) {
        val repo = FakeAuthRepository(
            resendResult = ApiResult.Failure(AppError.Network(null)),
        )
        val vm = VerifyEmailViewModel(repo, token = null)

        vm.resend()
        advanceUntilIdle()

        assertThat(vm.uiState.value.resendConfirmed).isFalse()
        assertThat(vm.uiState.value.message).isNotNull()
    }
}
