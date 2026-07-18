package com.interlinedlist.android.feature.profile.ui

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.profile.ui.search.UserSearchViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UserSearchViewModelTest {

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
    fun `debounced query runs a search and exposes results`() = runTest(dispatcher) {
        repo.searchResult = ApiResult.Success(listOf(testSearchResult("1", "ada"), testSearchResult("2", "adron")))
        val vm = UserSearchViewModel(repo)

        vm.onQueryChange("ad")
        advanceTimeBy(400)
        advanceUntilIdle()

        assertThat(repo.lastSearchQuery).isEqualTo("ad")
        assertThat(vm.uiState.value.results.map { it.username }).containsExactly("ada", "adron").inOrder()
        assertThat(vm.uiState.value.isSearching).isFalse()
    }

    @Test
    fun `queries shorter than the minimum length do not hit the api`() = runTest(dispatcher) {
        val vm = UserSearchViewModel(repo)

        vm.onQueryChange("a")
        advanceTimeBy(400)
        advanceUntilIdle()

        assertThat(repo.lastSearchQuery).isNull()
        assertThat(vm.uiState.value.results).isEmpty()
    }

    @Test
    fun `empty result over a valid query flags isEmptyResult`() = runTest(dispatcher) {
        repo.searchResult = ApiResult.Success(emptyList())
        val vm = UserSearchViewModel(repo)

        vm.onQueryChange("zzz")
        advanceTimeBy(400)
        advanceUntilIdle()

        assertThat(vm.uiState.value.isEmptyResult).isTrue()
    }

    @Test
    fun `search failure surfaces a mapped error and clears results`() = runTest(dispatcher) {
        repo.searchResult = ApiResult.Failure(AppError.Network("offline"))
        val vm = UserSearchViewModel(repo)

        vm.onQueryChange("ada")
        advanceTimeBy(400)
        advanceUntilIdle()

        assertThat(vm.uiState.value.errorMessage).isEqualTo("No connection. Check your network and try again.")
        assertThat(vm.uiState.value.results).isEmpty()
    }

    @Test
    fun `rapid typing debounces to a single search`() = runTest(dispatcher) {
        repo.searchResult = ApiResult.Success(listOf(testSearchResult("1", "ada")))
        val vm = UserSearchViewModel(repo)

        vm.onQueryChange("a")
        vm.onQueryChange("ad")
        vm.onQueryChange("ada")
        advanceTimeBy(400)
        advanceUntilIdle()

        // Only the final settled query should have been searched.
        assertThat(repo.lastSearchQuery).isEqualTo("ada")
    }
}
