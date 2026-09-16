package com.interlinedlist.android.feature.integrations.ui.accounts

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.integrations.domain.ConnectedAccount
import com.interlinedlist.android.feature.integrations.ui.FakeIntegrationsRepository
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
class ConnectedAccountsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeIntegrationsRepository

    private val linkedIn = ConnectedAccount(
        provider = ConnectedAccount.Provider.LINKEDIN,
        isConnected = true,
        handle = "Adron Hall",
        identityProvider = "linkedin",
        connectedAt = "2026-05-01T12:00:00Z",
        lastVerifiedAt = "2026-07-01T12:00:00Z",
    )
    private val bluesky = ConnectedAccount(ConnectedAccount.Provider.BLUESKY, isConnected = false)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeIntegrationsRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `loads accounts on init and clears loading`() = runTest(dispatcher) {
        repo.accounts = listOf(
            ConnectedAccount(
                ConnectedAccount.Provider.GITHUB,
                isConnected = true,
                handle = "@adron",
            ),
            bluesky,
        )

        val vm = ConnectedAccountsViewModel(repo)
        advanceUntilIdle()

        assertThat(vm.uiState.value.isLoading).isFalse()
        assertThat(vm.uiState.value.accounts).hasSize(2)
        assertThat(vm.uiState.value.accounts.first().handle).isEqualTo("@adron")
        assertThat(repo.accountsCalls).isEqualTo(1)
    }

    @Test
    fun `refresh re-queries the repository`() = runTest(dispatcher) {
        repo.accounts = emptyList()
        val vm = ConnectedAccountsViewModel(repo)
        advanceUntilIdle()

        vm.refresh()
        advanceUntilIdle()

        assertThat(repo.accountsCalls).isEqualTo(2)
    }

    // --- unlink ---

    @Test
    fun `unlink is confirmed before anything is sent`() = runTest(dispatcher) {
        repo.accounts = listOf(linkedIn)
        val vm = ConnectedAccountsViewModel(repo)
        advanceUntilIdle()

        vm.requestUnlink(linkedIn)
        advanceUntilIdle()

        // The confirmation is up, but nothing has been unlinked yet.
        assertThat(vm.uiState.value.unlinkCandidate).isEqualTo(linkedIn)
        assertThat(repo.unlinkedProviders).isEmpty()
        assertThat(vm.uiState.value.accounts).containsExactly(linkedIn)
    }

    @Test
    fun `dismissing the confirmation leaves the connection alone`() = runTest(dispatcher) {
        repo.accounts = listOf(linkedIn)
        val vm = ConnectedAccountsViewModel(repo)
        advanceUntilIdle()

        vm.requestUnlink(linkedIn)
        vm.dismissUnlinkRequest()
        advanceUntilIdle()

        assertThat(vm.uiState.value.unlinkCandidate).isNull()
        assertThat(repo.unlinkedProviders).isEmpty()
        assertThat(vm.uiState.value.accounts).containsExactly(linkedIn)
    }

    @Test
    fun `a confirmed unlink sends the identity provider and refreshes the list`() = runTest(dispatcher) {
        // First load has LinkedIn linked; the post-unlink reload no longer does.
        repo.queuedAccounts.addLast(listOf(linkedIn, bluesky))
        repo.queuedAccounts.addLast(
            listOf(ConnectedAccount(ConnectedAccount.Provider.LINKEDIN, isConnected = false), bluesky),
        )
        val vm = ConnectedAccountsViewModel(repo)
        advanceUntilIdle()

        vm.requestUnlink(linkedIn)
        vm.confirmUnlink()
        advanceUntilIdle()

        assertThat(repo.unlinkedProviders).containsExactly("linkedin")
        // The screen re-read the list rather than guessing at the new state.
        assertThat(repo.accountsCalls).isEqualTo(2)
        assertThat(vm.uiState.value.accounts.single { it.provider == ConnectedAccount.Provider.LINKEDIN }.isLinked)
            .isFalse()
        assertThat(vm.uiState.value.unlinkCandidate).isNull()
        assertThat(vm.uiState.value.pendingKeys).isEmpty()
        assertThat(vm.uiState.value.message)
            .isEqualTo("LinkedIn unlinked. Cross-posting to LinkedIn is off.")
        assertThat(vm.uiState.value.errorMessage).isNull()
    }

    @Test
    fun `a failed unlink keeps the connection and surfaces the server message`() = runTest(dispatcher) {
        repo.accounts = listOf(linkedIn, bluesky)
        repo.unlinkResult = ApiResult.Failure(AppError.Unknown("Identity not found"))
        val vm = ConnectedAccountsViewModel(repo)
        advanceUntilIdle()

        vm.requestUnlink(linkedIn)
        vm.confirmUnlink()
        advanceUntilIdle()

        assertThat(repo.unlinkedProviders).containsExactly("linkedin")
        assertThat(vm.uiState.value.errorMessage).isEqualTo("Identity not found")
        // The row is still there, and no pointless refresh was issued.
        assertThat(vm.uiState.value.accounts).contains(linkedIn)
        assertThat(repo.accountsCalls).isEqualTo(1)
        assertThat(vm.uiState.value.pendingKeys).isEmpty()
        assertThat(vm.uiState.value.message).isNull()
    }

    @Test
    fun `unlink is ignored for a provider with nothing linked`() = runTest(dispatcher) {
        repo.accounts = listOf(bluesky)
        val vm = ConnectedAccountsViewModel(repo)
        advanceUntilIdle()

        vm.requestUnlink(bluesky)
        vm.confirmUnlink()
        advanceUntilIdle()

        assertThat(vm.uiState.value.unlinkCandidate).isNull()
        assertThat(repo.unlinkedProviders).isEmpty()
    }

    // --- verify ---

    @Test
    fun `verify sends the identity provider and refreshes the list`() = runTest(dispatcher) {
        val verified = linkedIn.copy(lastVerifiedAt = "2026-09-16T12:00:00Z")
        repo.queuedAccounts.addLast(listOf(linkedIn))
        repo.queuedAccounts.addLast(listOf(verified))
        val vm = ConnectedAccountsViewModel(repo)
        advanceUntilIdle()

        vm.verify(linkedIn)
        advanceUntilIdle()

        assertThat(repo.verifiedProviders).containsExactly("linkedin")
        assertThat(repo.accountsCalls).isEqualTo(2)
        assertThat(vm.uiState.value.accounts.single().lastVerifiedAt).isEqualTo("2026-09-16T12:00:00Z")
        assertThat(vm.uiState.value.message).isEqualTo("LinkedIn connection verified.")
        assertThat(vm.uiState.value.pendingKeys).isEmpty()
    }

    @Test
    fun `a failed verify surfaces the server message and leaves the row untouched`() = runTest(dispatcher) {
        repo.accounts = listOf(linkedIn)
        repo.verifyResult = ApiResult.Failure(AppError.Unknown("LinkedIn account not linked"))
        val vm = ConnectedAccountsViewModel(repo)
        advanceUntilIdle()

        vm.verify(linkedIn)
        advanceUntilIdle()

        assertThat(vm.uiState.value.errorMessage).isEqualTo("LinkedIn account not linked")
        assertThat(vm.uiState.value.accounts).containsExactly(linkedIn)
        assertThat(repo.accountsCalls).isEqualTo(1)
    }

    @Test
    fun `a second tap while a verify is in flight is ignored`() = runTest(dispatcher) {
        repo.accounts = listOf(linkedIn)
        val vm = ConnectedAccountsViewModel(repo)
        advanceUntilIdle()

        vm.verify(linkedIn)
        vm.verify(linkedIn)
        advanceUntilIdle()

        assertThat(repo.verifiedProviders).containsExactly("linkedin")
    }

    @Test
    fun `snackbar text is cleared once shown`() = runTest(dispatcher) {
        repo.accounts = listOf(linkedIn)
        repo.verifyResult = ApiResult.Failure(AppError.Unknown("boom"))
        val vm = ConnectedAccountsViewModel(repo)
        advanceUntilIdle()

        vm.verify(linkedIn)
        advanceUntilIdle()
        vm.clearError()

        assertThat(vm.uiState.value.errorMessage).isNull()

        repo.verifyResult = ApiResult.Success(Unit)
        vm.verify(linkedIn)
        advanceUntilIdle()
        vm.clearMessage()

        assertThat(vm.uiState.value.message).isNull()
    }
}
