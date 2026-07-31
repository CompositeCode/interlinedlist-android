package com.interlinedlist.android.feature.profile.ui

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.profile.ui.account.ConnectedAccountsViewModel
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
    private lateinit var repo: FakeProfileRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeProfileRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `loads identities`() = runTest(dispatcher) {
        repo.identitiesResult = ApiResult.Success(
            listOf(
                testIdentity(id = "i1", provider = "linkedin"),
                testIdentity(id = "i2", provider = "mastodon:techhub.social"),
            ),
        )

        val vm = ConnectedAccountsViewModel(repo)
        advanceUntilIdle()

        assertThat(repo.identitiesCount).isEqualTo(1)
        assertThat(vm.uiState.value.identities.map { it.id }).containsExactly("i1", "i2").inOrder()
        assertThat(vm.uiState.value.isLoading).isFalse()
    }

    @Test
    fun `unlink removes the row optimistically and calls the repository with the provider`() = runTest(dispatcher) {
        repo.identitiesResult = ApiResult.Success(
            listOf(
                testIdentity(id = "i1", provider = "linkedin"),
                testIdentity(id = "i2", provider = "mastodon:techhub.social"),
            ),
        )
        val vm = ConnectedAccountsViewModel(repo)
        advanceUntilIdle()

        vm.unlink("i2")
        advanceUntilIdle()

        // The API keys on the provider, not the identity id.
        assertThat(repo.unlinkedProvider).isEqualTo("mastodon:techhub.social")
        assertThat(vm.uiState.value.identities.map { it.id }).containsExactly("i1")
        assertThat(vm.uiState.value.pendingUnlinkIds).isEmpty()
    }

    @Test
    fun `unlink rolls the row back and surfaces an error on failure`() = runTest(dispatcher) {
        repo.identitiesResult = ApiResult.Success(listOf(testIdentity(id = "i1", provider = "linkedin")))
        repo.unlinkIdentityResult = ApiResult.Failure(AppError.Server("boom"))
        val vm = ConnectedAccountsViewModel(repo)
        advanceUntilIdle()

        vm.unlink("i1")
        advanceUntilIdle()

        assertThat(vm.uiState.value.identities.map { it.id }).containsExactly("i1")
        assertThat(vm.uiState.value.errorMessage).isNotNull()
        assertThat(vm.uiState.value.pendingUnlinkIds).isEmpty()
    }

    @Test
    fun `a load failure surfaces a mapped error`() = runTest(dispatcher) {
        repo.identitiesResult = ApiResult.Failure(AppError.Network("offline"))

        val vm = ConnectedAccountsViewModel(repo)
        advanceUntilIdle()

        assertThat(vm.uiState.value.errorMessage).isEqualTo("No connection. Check your network and try again.")
    }
}
