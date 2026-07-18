package com.interlinedlist.android.feature.integrations.ui.accounts

import com.google.common.truth.Truth.assertThat
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
            ConnectedAccount(ConnectedAccount.Provider.GITHUB, isConnected = true, handle = "@adron"),
            ConnectedAccount(ConnectedAccount.Provider.BLUESKY, isConnected = false),
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
}
