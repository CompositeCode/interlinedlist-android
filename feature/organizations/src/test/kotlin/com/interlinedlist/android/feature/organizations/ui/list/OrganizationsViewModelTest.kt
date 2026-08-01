package com.interlinedlist.android.feature.organizations.ui.list

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.organizations.FakeOrganizationsRepository
import com.interlinedlist.android.feature.organizations.domain.Organization
import com.interlinedlist.android.feature.organizations.domain.Paged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrganizationsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private fun org(id: String) = Organization(id, "Org $id", null, null, false, 0, null, null)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `init refreshes and streams cached organizations from the repository`() = runTest(dispatcher) {
        val repo = FakeOrganizationsRepository().apply {
            refreshResult = ApiResult.Success(
                Paged(listOf(org("1"), org("2")), hasMore = true, total = 5, offset = 2),
            )
        }
        val vm = OrganizationsViewModel(repo)

        vm.uiState.test {
            awaitItem() // initial
            advanceUntilIdle()
            val loaded = expectMostRecentItem()
            assertThat(loaded.organizations.map { it.id }).containsExactly("1", "2").inOrder()
            assertThat(loaded.isRefreshing).isFalse()
            assertThat(loaded.hasMore).isTrue()
            assertThat(loaded.nextOffset).isEqualTo(2)
        }
        assertThat(repo.refreshCount).isEqualTo(1)
    }

    @Test
    fun `refresh failure surfaces error but keeps cached organizations visible`() = runTest(dispatcher) {
        val repo = FakeOrganizationsRepository().apply {
            cache.value = listOf(org("cached"))
            refreshResult = ApiResult.Failure(AppError.Network("offline"))
        }
        val vm = OrganizationsViewModel(repo)
        backgroundScope.launch { vm.uiState.collect { } } // keep the combined flow active
        advanceUntilIdle()

        val state = vm.uiState.value
        // Offline-first: the Room stream still shows what was cached.
        assertThat(state.organizations.map { it.id }).containsExactly("cached")
        assertThat(state.errorMessage).isNotNull()
        assertThat(state.isRefreshing).isFalse()
    }

    @Test
    fun `subscription gate is flagged for an upsell state`() = runTest(dispatcher) {
        val repo = FakeOrganizationsRepository().apply {
            refreshResult = FakeOrganizationsRepository.subscriptionFailure()
        }
        val vm = OrganizationsViewModel(repo)
        backgroundScope.launch { vm.uiState.collect { } }
        advanceUntilIdle()

        assertThat(vm.uiState.value.subscriptionRequired).isTrue()
    }

    @Test
    fun `loadMore appends the next page and updates pagination`() = runTest(dispatcher) {
        val repo = FakeOrganizationsRepository().apply {
            refreshResult = ApiResult.Success(Paged(listOf(org("1")), hasMore = true, total = 2, offset = 1))
            loadMoreResult = ApiResult.Success(Paged(listOf(org("2")), hasMore = false, total = 2, offset = 2))
        }
        val vm = OrganizationsViewModel(repo)
        backgroundScope.launch { vm.uiState.collect { } }
        advanceUntilIdle()

        vm.loadMore()
        advanceUntilIdle()

        assertThat(repo.loadMoreCount).isEqualTo(1)
        assertThat(vm.uiState.value.organizations.map { it.id }).containsExactly("1", "2").inOrder()
        assertThat(vm.uiState.value.hasMore).isFalse()
    }

    @Test
    fun `loadMore is skipped when no more pages remain`() = runTest(dispatcher) {
        val repo = FakeOrganizationsRepository().apply {
            refreshResult = ApiResult.Success(Paged(listOf(org("1")), hasMore = false, total = 1, offset = 1))
        }
        val vm = OrganizationsViewModel(repo)
        advanceUntilIdle()

        vm.loadMore()
        advanceUntilIdle()

        assertThat(repo.loadMoreCount).isEqualTo(0)
    }

    @Test
    fun `createOrganization reports the created org id via callback`() = runTest(dispatcher) {
        val repo = FakeOrganizationsRepository()
        val vm = OrganizationsViewModel(repo)
        advanceUntilIdle()

        var createdId: String? = null
        vm.createOrganization("Acme", "desc", isPublic = true) { createdId = it.id }
        advanceUntilIdle()

        assertThat(createdId).isEqualTo("new")
    }

    @Test
    fun `createOrganization surfaces the subscription gate on failure`() = runTest(dispatcher) {
        val repo = FakeOrganizationsRepository().apply {
            createResult = FakeOrganizationsRepository.subscriptionFailure()
        }
        val vm = OrganizationsViewModel(repo)
        backgroundScope.launch { vm.uiState.collect { } }
        advanceUntilIdle()

        vm.createOrganization("Acme", null)
        advanceUntilIdle()

        assertThat(vm.uiState.value.subscriptionRequired).isTrue()
    }
}
