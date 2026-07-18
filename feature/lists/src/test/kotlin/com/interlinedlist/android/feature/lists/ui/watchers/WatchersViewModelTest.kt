package com.interlinedlist.android.feature.lists.ui.watchers

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.lists.FakeListsRepository
import com.interlinedlist.android.feature.lists.domain.Watcher
import com.interlinedlist.android.feature.lists.domain.WatcherCandidate
import com.interlinedlist.android.feature.lists.domain.WatcherRole
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
class WatchersViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private fun watcher(id: String, role: WatcherRole = WatcherRole.VIEWER) =
        Watcher(id, "user$id", "User $id", null, role)

    private fun viewModel(repo: FakeListsRepository) =
        WatchersViewModel(repo, SavedStateHandle(mapOf(WATCHERS_LIST_ID_ARG to "L1")))

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `loads watchers and watching status on init`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply {
            watchersResult = ApiResult.Success(listOf(watcher("1"), watcher("2")))
            isWatchingResult = ApiResult.Success(true)
        }
        val vm = viewModel(repo)

        vm.uiState.test {
            awaitItem() // initial
            advanceUntilIdle()
            val loaded = expectMostRecentItem()
            assertThat(loaded.isLoading).isFalse()
            assertThat(loaded.watchers.map { it.userId }).containsExactly("1", "2").inOrder()
            assertThat(loaded.isWatching).isTrue()
        }
    }

    @Test
    fun `search surfaces candidate users and clearing empties them`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply {
            candidatesResult = ApiResult.Success(listOf(WatcherCandidate("u9", "linus", "Linus", null)))
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onSearchQueryChange("lin")
        advanceUntilIdle()
        assertThat(vm.uiState.value.candidates.map { it.userId }).containsExactly("u9")
        assertThat(repo.lastWatcherSearch).isEqualTo("lin")

        vm.onSearchQueryChange("")
        advanceUntilIdle()
        assertThat(vm.uiState.value.candidates).isEmpty()
    }

    @Test
    fun `adding a candidate clears the search and reloads watchers`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply {
            watchersResult = ApiResult.Success(listOf(watcher("1")))
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        // After add, load() returns the (now larger) watcher set.
        repo.watchersResult = ApiResult.Success(listOf(watcher("1"), watcher("u9")))
        vm.addWatcher(WatcherCandidate("u9", "linus", null, null))
        advanceUntilIdle()

        assertThat(repo.addWatcherCount).isEqualTo(1)
        assertThat(vm.uiState.value.searchQuery).isEmpty()
        assertThat(vm.uiState.value.watchers.map { it.userId }).containsExactly("1", "u9").inOrder()
    }

    @Test
    fun `changeRole updates the matching watcher optimistically`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply {
            watchersResult = ApiResult.Success(listOf(watcher("1", WatcherRole.VIEWER)))
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.changeRole(vm.uiState.value.watchers.single(), WatcherRole.EDITOR)
        advanceUntilIdle()

        assertThat(vm.uiState.value.watchers.single().role).isEqualTo(WatcherRole.EDITOR)
    }

    @Test
    fun `removeWatcher drops the watcher from state`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply {
            watchersResult = ApiResult.Success(listOf(watcher("1"), watcher("2")))
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.removeWatcher(vm.uiState.value.watchers.first { it.userId == "1" })
        advanceUntilIdle()

        assertThat(repo.removeWatcherCount).isEqualTo(1)
        assertThat(vm.uiState.value.watchers.map { it.userId }).containsExactly("2")
    }

    @Test
    fun `load failure surfaces an error`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply {
            watchersResult = ApiResult.Failure(
                com.interlinedlist.android.core.common.result.AppError.Network("offline"),
            )
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        assertThat(vm.uiState.value.errorMessage).isNotNull()
        assertThat(vm.uiState.value.isLoading).isFalse()
    }
}
