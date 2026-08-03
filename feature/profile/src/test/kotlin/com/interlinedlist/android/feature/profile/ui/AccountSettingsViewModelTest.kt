package com.interlinedlist.android.feature.profile.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.profile.ui.account.AccountSettingsEffect
import com.interlinedlist.android.feature.profile.ui.account.AccountSettingsViewModel
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
class AccountSettingsViewModelTest {

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
    fun `seeds the username from the cached current user`() = runTest(dispatcher) {
        repo.currentUserFlow.value = testUser(username = "adron")

        val vm = AccountSettingsViewModel(repo)
        advanceUntilIdle()

        assertThat(vm.uiState.value.username).isEqualTo("adron")
    }

    @Test
    fun `requestEmailChange sends the trimmed email and flags success`() = runTest(dispatcher) {
        repo.requestEmailChangeResult = ApiResult.Success(Unit)
        val vm = AccountSettingsViewModel(repo)
        advanceUntilIdle()

        vm.requestEmailChange("  new@example.com  ")
        advanceUntilIdle()

        assertThat(repo.requestedEmail).isEqualTo("new@example.com")
        assertThat(vm.uiState.value.emailChangeRequested).isTrue()
        assertThat(vm.uiState.value.isChangingEmail).isFalse()
    }

    @Test
    fun `requestEmailChange surfaces an error on failure`() = runTest(dispatcher) {
        repo.requestEmailChangeResult = ApiResult.Failure(AppError.Conflict("Email in use"))
        val vm = AccountSettingsViewModel(repo)
        advanceUntilIdle()

        vm.requestEmailChange("taken@example.com")
        advanceUntilIdle()

        assertThat(vm.uiState.value.emailChangeRequested).isFalse()
        assertThat(vm.uiState.value.errorMessage).isNotNull()
    }

    @Test
    fun `deleteAccount confirms with the seeded username and emits a signed-out effect`() = runTest(dispatcher) {
        repo.currentUserFlow.value = testUser(username = "adron")
        repo.deleteAccountResult = ApiResult.Success(Unit)
        val vm = AccountSettingsViewModel(repo)
        advanceUntilIdle()

        vm.effects.test {
            vm.deleteAccount("adron@example.com")
            advanceUntilIdle()

            assertThat(awaitItem()).isEqualTo(AccountSettingsEffect.SignedOut)
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(repo.deleteAccountArgs).isEqualTo("adron" to "adron@example.com")
        assertThat(vm.uiState.value.isDeletingAccount).isFalse()
    }

    @Test
    fun `deleteAccount surfaces an error and emits no effect on failure`() = runTest(dispatcher) {
        repo.currentUserFlow.value = testUser(username = "adron")
        repo.deleteAccountResult = ApiResult.Failure(AppError.Forbidden("Confirmation did not match"))
        val vm = AccountSettingsViewModel(repo)
        advanceUntilIdle()

        vm.effects.test {
            vm.deleteAccount("wrong@example.com")
            advanceUntilIdle()

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(vm.uiState.value.errorMessage).isNotNull()
        assertThat(vm.uiState.value.isDeletingAccount).isFalse()
    }
}
