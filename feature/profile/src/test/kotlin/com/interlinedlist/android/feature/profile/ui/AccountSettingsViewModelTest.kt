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

    // ---- pending email change ---------------------------------------------

    @Test
    fun `the pending change renders from the server's pendingEmail`() = runTest(dispatcher) {
        repo.pendingEmailChangeResult = ApiResult.Success("new@example.com")

        val vm = AccountSettingsViewModel(repo)
        advanceUntilIdle()

        assertThat(vm.uiState.value.pendingEmail).isEqualTo("new@example.com")
    }

    @Test
    fun `no pending change means no banner`() = runTest(dispatcher) {
        repo.pendingEmailChangeResult = ApiResult.Success(null)

        val vm = AccountSettingsViewModel(repo)
        advanceUntilIdle()

        assertThat(vm.uiState.value.pendingEmail).isNull()
    }

    @Test
    fun `the pending change clears once the server reports it verified`() = runTest(dispatcher) {
        // First read: still awaiting confirmation. Second read (after the user opened
        // the emailed link): the server has cleared pendingEmail.
        repo.pendingEmailChangeResults = ArrayDeque(
            listOf(ApiResult.Success("new@example.com"), ApiResult.Success(null)),
        )

        val vm = AccountSettingsViewModel(repo)
        advanceUntilIdle()
        assertThat(vm.uiState.value.pendingEmail).isEqualTo("new@example.com")

        vm.refreshPendingEmailChange()
        advanceUntilIdle()

        assertThat(vm.uiState.value.pendingEmail).isNull()
    }

    @Test
    fun `a successful request puts the screen straight into the pending state`() = runTest(dispatcher) {
        repo.pendingEmailChangeResult = ApiResult.Success(null)
        repo.requestEmailChangeResult = ApiResult.Success(Unit)
        val vm = AccountSettingsViewModel(repo)
        advanceUntilIdle()

        repo.pendingEmailChangeResult = ApiResult.Success("new@example.com")
        vm.requestEmailChange("new@example.com")
        advanceUntilIdle()

        assertThat(vm.uiState.value.pendingEmail).isEqualTo("new@example.com")
    }

    @Test
    fun `resend re-requests the change for the pending address`() = runTest(dispatcher) {
        repo.pendingEmailChangeResult = ApiResult.Success("new@example.com")
        repo.requestEmailChangeResult = ApiResult.Success(Unit)
        val vm = AccountSettingsViewModel(repo)
        advanceUntilIdle()

        vm.resendEmailChange()
        advanceUntilIdle()

        assertThat(repo.requestedEmails).containsExactly("new@example.com")
        assertThat(vm.uiState.value.emailChangeResent).isTrue()
        assertThat(vm.uiState.value.isResendingEmailChange).isFalse()
    }

    @Test
    fun `resend surfaces the server's message on failure`() = runTest(dispatcher) {
        repo.pendingEmailChangeResult = ApiResult.Success("new@example.com")
        repo.requestEmailChangeResult =
            ApiResult.Failure(AppError.Conflict("That email is already in use"))
        val vm = AccountSettingsViewModel(repo)
        advanceUntilIdle()

        vm.resendEmailChange()
        advanceUntilIdle()

        assertThat(vm.uiState.value.emailChangeResent).isFalse()
        assertThat(vm.uiState.value.errorMessage).isEqualTo("That email is already in use")
    }

    @Test
    fun `resend does nothing when no change is pending`() = runTest(dispatcher) {
        repo.pendingEmailChangeResult = ApiResult.Success(null)
        val vm = AccountSettingsViewModel(repo)
        advanceUntilIdle()

        vm.resendEmailChange()
        advanceUntilIdle()

        assertThat(repo.requestedEmails).isEmpty()
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
