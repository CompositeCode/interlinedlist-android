package com.interlinedlist.android.feature.lists.ui.list

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.lists.FakeListsRepository
import com.interlinedlist.android.feature.lists.domain.ListSummary
import com.interlinedlist.android.feature.lists.domain.Paged
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
class ListsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private fun summary(id: String) = ListSummary(id, "List $id", null, 0, null, false, null)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `init refreshes and streams cached lists from the repository`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply {
            refreshResult = ApiResult.Success(
                Paged(listOf(summary("1"), summary("2")), hasMore = true, total = 5, offset = 2),
            )
        }
        val vm = ListsViewModel(repo)

        vm.uiState.test {
            awaitItem() // initial
            advanceUntilIdle()
            val loaded = expectMostRecentItem()
            assertThat(loaded.lists.map { it.id }).containsExactly("1", "2").inOrder()
            assertThat(loaded.isRefreshing).isFalse()
            assertThat(loaded.hasMore).isTrue()
            assertThat(loaded.nextOffset).isEqualTo(2)
        }
        assertThat(repo.refreshCount).isEqualTo(1)
    }

    @Test
    fun `refresh failure surfaces error but keeps cached lists visible`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply {
            cache.value = listOf(summary("cached"))
            refreshResult = ApiResult.Failure(
                com.interlinedlist.android.core.common.result.AppError.Network("offline"),
            )
        }
        val vm = ListsViewModel(repo)
        backgroundScope.launch { vm.uiState.collect { } } // keep the combined flow active
        advanceUntilIdle()

        val state = vm.uiState.value
        // Offline-first: the Room stream still shows what was cached.
        assertThat(state.lists.map { it.id }).containsExactly("cached")
        assertThat(state.errorMessage).isNotNull()
        assertThat(state.isRefreshing).isFalse()
    }

    @Test
    fun `subscription gate is flagged for an upsell state`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply { refreshResult = FakeListsRepository.subscriptionFailure() }
        val vm = ListsViewModel(repo)
        backgroundScope.launch { vm.uiState.collect { } }
        advanceUntilIdle()

        assertThat(vm.uiState.value.subscriptionRequired).isTrue()
    }

    @Test
    fun `loadMore appends the next page and updates pagination`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply {
            refreshResult = ApiResult.Success(Paged(listOf(summary("1")), hasMore = true, total = 2, offset = 1))
            loadMoreResult = ApiResult.Success(Paged(listOf(summary("2")), hasMore = false, total = 2, offset = 2))
        }
        val vm = ListsViewModel(repo)
        backgroundScope.launch { vm.uiState.collect { } }
        advanceUntilIdle()

        vm.loadMore()
        advanceUntilIdle()

        assertThat(repo.loadMoreCount).isEqualTo(1)
        assertThat(vm.uiState.value.lists.map { it.id }).containsExactly("1", "2").inOrder()
        assertThat(vm.uiState.value.hasMore).isFalse()
    }

    @Test
    fun `loadMore is skipped when no more pages remain`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply {
            refreshResult = ApiResult.Success(Paged(listOf(summary("1")), hasMore = false, total = 1, offset = 1))
        }
        val vm = ListsViewModel(repo)
        advanceUntilIdle()

        vm.loadMore()
        advanceUntilIdle()

        assertThat(repo.loadMoreCount).isEqualTo(0)
    }

    @Test
    fun `createList reports the created list id via callback`() = runTest(dispatcher) {
        val repo = FakeListsRepository()
        val vm = ListsViewModel(repo)
        advanceUntilIdle()

        var createdId: String? = null
        vm.createList("Groceries", null) { createdId = it.id }
        advanceUntilIdle()

        assertThat(createdId).isEqualTo("new")
    }

    @Test
    fun `search shows server results and clearing restores the cached index`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply {
            refreshResult = ApiResult.Success(Paged(listOf(summary("cached")), hasMore = false, total = 1, offset = 1))
            searchResult = ApiResult.Success(listOf(summary("hit")))
        }
        val vm = ListsViewModel(repo)
        backgroundScope.launch { vm.uiState.collect { } }
        advanceUntilIdle()

        vm.onSearchQueryChange("hit")
        advanceUntilIdle()
        assertThat(vm.uiState.value.visibleLists.map { it.id }).containsExactly("hit")
        assertThat(vm.uiState.value.isSearching).isTrue()

        vm.onSearchQueryChange("")
        advanceUntilIdle()
        assertThat(vm.uiState.value.visibleLists.map { it.id }).containsExactly("cached")
        assertThat(vm.uiState.value.isSearching).isFalse()
    }

    @Test
    fun `deleteList removes the row from the cached stream`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply {
            refreshResult = ApiResult.Success(
                Paged(listOf(summary("1"), summary("2")), hasMore = false, total = 2, offset = 2),
            )
        }
        val vm = ListsViewModel(repo)
        backgroundScope.launch { vm.uiState.collect { } }
        advanceUntilIdle()

        vm.deleteList("1")
        advanceUntilIdle()

        assertThat(vm.uiState.value.lists.map { it.id }).containsExactly("2")
    }
}
