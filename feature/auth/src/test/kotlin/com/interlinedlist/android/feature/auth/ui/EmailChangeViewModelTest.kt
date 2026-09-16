package com.interlinedlist.android.feature.auth.ui

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.auth.nav.EmailChangeAction
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
class EmailChangeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a verify link confirms the change with the emailed token`() = runTest(dispatcher) {
        val repo = FakeAuthRepository(verifyEmailChangeResult = ApiResult.Success(Unit))

        val vm = EmailChangeViewModel(repo, EmailChangeAction.VERIFY, token = "verify-tok")
        advanceUntilIdle()

        assertThat(repo.lastVerifyEmailChangeToken).isEqualTo("verify-tok")
        assertThat(repo.lastUndoEmailChangeToken).isNull()
        assertThat(vm.uiState.value.status).isEqualTo(EmailChangeStatus.DONE)
        assertThat(vm.uiState.value.action).isEqualTo(EmailChangeAction.VERIFY)
    }

    @Test
    fun `an undo link reverts the change with the emailed token`() = runTest(dispatcher) {
        val repo = FakeAuthRepository(undoEmailChangeResult = ApiResult.Success(Unit))

        val vm = EmailChangeViewModel(repo, EmailChangeAction.UNDO, token = "undo-tok")
        advanceUntilIdle()

        assertThat(repo.lastUndoEmailChangeToken).isEqualTo("undo-tok")
        assertThat(repo.lastVerifyEmailChangeToken).isNull()
        assertThat(vm.uiState.value.status).isEqualTo(EmailChangeStatus.DONE)
        assertThat(vm.uiState.value.action).isEqualTo(EmailChangeAction.UNDO)
    }

    @Test
    fun `a rejected verify token surfaces the server's own message`() = runTest(dispatcher) {
        val repo = FakeAuthRepository(
            verifyEmailChangeResult = ApiResult.Failure(AppError.Conflict("That email is already in use")),
        )

        val vm = EmailChangeViewModel(repo, EmailChangeAction.VERIFY, token = "stale")
        advanceUntilIdle()

        assertThat(vm.uiState.value.status).isEqualTo(EmailChangeStatus.FAILED)
        assertThat(vm.uiState.value.message).isEqualTo("That email is already in use")
    }

    @Test
    fun `a rejected undo token surfaces the server's own message`() = runTest(dispatcher) {
        val repo = FakeAuthRepository(
            undoEmailChangeResult = ApiResult.Failure(AppError.Unknown("Undo link has expired")),
        )

        val vm = EmailChangeViewModel(repo, EmailChangeAction.UNDO, token = "stale")
        advanceUntilIdle()

        assertThat(vm.uiState.value.status).isEqualTo(EmailChangeStatus.FAILED)
        assertThat(vm.uiState.value.message).isEqualTo("Undo link has expired")
    }

    @Test
    fun `a link with no token never reaches the server and never reports success`() = runTest(dispatcher) {
        val repo = FakeAuthRepository()

        val vm = EmailChangeViewModel(repo, EmailChangeAction.UNDO, token = null)
        advanceUntilIdle()

        assertThat(repo.lastUndoEmailChangeToken).isNull()
        assertThat(repo.lastVerifyEmailChangeToken).isNull()
        assertThat(vm.uiState.value.status).isEqualTo(EmailChangeStatus.INVALID_LINK)
    }

    @Test
    fun `a blank token is treated as an invalid link`() = runTest(dispatcher) {
        val repo = FakeAuthRepository()

        val vm = EmailChangeViewModel(repo, EmailChangeAction.VERIFY, token = "   ")
        advanceUntilIdle()

        assertThat(repo.lastVerifyEmailChangeToken).isNull()
        assertThat(vm.uiState.value.status).isEqualTo(EmailChangeStatus.INVALID_LINK)
    }
}
