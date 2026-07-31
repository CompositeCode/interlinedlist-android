package com.interlinedlist.android.feature.lists.ui.folders

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.lists.FakeListsRepository
import com.interlinedlist.android.feature.lists.domain.ListFolder
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
class FolderBrowserViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val folders = listOf(
        ListFolder("f1", "Work", null),
        ListFolder("f2", "Personal", null),
    )

    private fun repoWithFolders() = FakeListsRepository().apply {
        foldersResult = ApiResult.Success(folders)
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `loads folders on init`() = runTest(dispatcher) {
        val vm = FolderBrowserViewModel(repoWithFolders())
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.folders.map { it.id }).containsExactly("f1", "f2").inOrder()
    }

    @Test
    fun `renameFolder optimistically renames then confirms from the server`() = runTest(dispatcher) {
        val repo = repoWithFolders().apply {
            updateFolderResult = ApiResult.Success(ListFolder("f1", "Archive", null))
        }
        val vm = FolderBrowserViewModel(repo)
        advanceUntilIdle()

        vm.uiState.test {
            assertThat(awaitItem().folders.first().name).isEqualTo("Work")
            vm.renameFolder(folders[0], "Archive")
            // Optimistic emission renames immediately.
            assertThat(awaitItem().folders.first().name).isEqualTo("Archive")
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(repo.updateFolderCount).isEqualTo(1)
        assertThat(repo.lastUpdatedFolderName).isEqualTo("Archive")
        assertThat(vm.uiState.value.folders.first().name).isEqualTo("Archive")
    }

    @Test
    fun `renameFolder rolls back on failure`() = runTest(dispatcher) {
        val repo = repoWithFolders().apply {
            updateFolderResult = FakeListsRepository.subscriptionFailure()
        }
        val vm = FolderBrowserViewModel(repo)
        advanceUntilIdle()

        vm.renameFolder(folders[0], "Archive")
        advanceUntilIdle()

        // Rolled back to the original name.
        assertThat(vm.uiState.value.folders.first().name).isEqualTo("Work")
        assertThat(vm.uiState.value.errorMessage).isNotNull()
    }

    @Test
    fun `deleteFolder optimistically removes then stays removed on success`() = runTest(dispatcher) {
        val repo = repoWithFolders()
        val vm = FolderBrowserViewModel(repo)
        advanceUntilIdle()

        vm.deleteFolder(folders[0])
        advanceUntilIdle()

        assertThat(repo.deleteFolderCount).isEqualTo(1)
        assertThat(repo.lastDeletedFolderId).isEqualTo("f1")
        assertThat(vm.uiState.value.folders.map { it.id }).containsExactly("f2")
    }

    @Test
    fun `deleteFolder restores the row on failure`() = runTest(dispatcher) {
        val repo = repoWithFolders().apply {
            deleteFolderResult = FakeListsRepository.subscriptionFailure()
        }
        val vm = FolderBrowserViewModel(repo)
        advanceUntilIdle()

        vm.deleteFolder(folders[0])
        advanceUntilIdle()

        // Restored after the failed delete.
        assertThat(vm.uiState.value.folders.map { it.id }).containsExactly("f1", "f2").inOrder()
        assertThat(vm.uiState.value.errorMessage).isNotNull()
    }

    @Test
    fun `moveFolder updates the parent optimistically`() = runTest(dispatcher) {
        val nested = listOf(ListFolder("f1", "Work", null), ListFolder("f2", "Personal", "f1"))
        val repo = FakeListsRepository().apply {
            foldersResult = ApiResult.Success(nested)
            updateFolderResult = ApiResult.Success(ListFolder("f2", "Personal", null))
        }
        val vm = FolderBrowserViewModel(repo)
        advanceUntilIdle()

        vm.moveFolder(nested[1], newParentId = null)
        advanceUntilIdle()

        assertThat(repo.lastUpdatedFolderParentId).isNull()
        assertThat(vm.uiState.value.folders.first { it.id == "f2" }.parentId).isNull()
    }
}
