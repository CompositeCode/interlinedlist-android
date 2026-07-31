package com.interlinedlist.android.feature.documents.ui

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.documents.domain.DocumentFolder
import com.interlinedlist.android.feature.documents.domain.FolderNode
import com.interlinedlist.android.feature.documents.ui.browser.DocumentsBrowserViewModel
import com.interlinedlist.android.feature.documents.ui.browser.FOLDER_ID_ARG
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
class DocumentsBrowserViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeDocumentsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeDocumentsRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun rootViewModel() =
        DocumentsBrowserViewModel(repo, SavedStateHandle())

    private fun folderViewModel(folderId: String) =
        DocumentsBrowserViewModel(repo, SavedStateHandle(mapOf(FOLDER_ID_ARG to folderId)))

    @Test
    fun `root level shows top-level folders and unfiled documents`() = runTest(dispatcher) {
        repo.folders.value = listOf(DocumentFolder("f1", "Work", null))
        repo.documents.value = listOf(
            testDocument("root1"),
            testDocument("inWork", folderId = "f1"),
        )

        val vm = rootViewModel()
        advanceUntilIdle()

        val contents = vm.uiState.value.contents
        assertThat(contents.folderId).isEqualTo(FolderNode.ROOT_ID)
        assertThat(contents.subfolders.map { it.id }).containsExactly("f1")
        assertThat(contents.documents.map { it.id }).containsExactly("root1")
        assertThat(vm.uiState.value.isLoading).isFalse()
    }

    @Test
    fun `opening a folder level shows its documents and breadcrumb`() = runTest(dispatcher) {
        repo.folders.value = listOf(
            DocumentFolder("f1", "Work", null),
            DocumentFolder("f2", "Reports", "f1"),
        )
        repo.documents.value = listOf(testDocument("d1", folderId = "f2"))

        val vm = folderViewModel("f2")
        advanceUntilIdle()

        val contents = vm.uiState.value.contents
        assertThat(contents.folderId).isEqualTo("f2")
        assertThat(contents.documents.map { it.id }).containsExactly("d1")
        assertThat(contents.breadcrumb.map { it.name })
            .containsExactly(FolderNode.ROOT_NAME, "Work", "Reports").inOrder()
    }

    @Test
    fun `refresh failure surfaces a mapped error`() = runTest(dispatcher) {
        repo.refreshTreeResult = ApiResult.Failure(AppError.Network("offline"))

        val vm = rootViewModel()
        advanceUntilIdle()

        assertThat(vm.uiState.value.errorMessage)
            .isEqualTo("No connection. Check your network and try again.")
        assertThat(vm.uiState.value.subscriptionRequired).isFalse()
    }

    @Test
    fun `subscription gate is flagged on a subscription-required failure`() = runTest(dispatcher) {
        repo.refreshTreeResult = ApiResult.Failure(AppError.SubscriptionRequired("Subscribe to use documents"))

        val vm = rootViewModel()
        advanceUntilIdle()

        assertThat(vm.uiState.value.subscriptionRequired).isTrue()
        assertThat(vm.uiState.value.errorMessage).isEqualTo("Subscribe to use documents")
    }

    @Test
    fun `create document in a folder uses the create-in-folder endpoint and opens the new doc`() =
        runTest(dispatcher) {
            repo.createResult = ApiResult.Success(testDocument("new-id", title = "Untitled", folderId = "f1"))

            val vm = folderViewModel("f1")
            advanceUntilIdle()

            var createdId: String? = null
            vm.createDocument(title = "Untitled") { createdId = it }
            advanceUntilIdle()

            assertThat(createdId).isEqualTo("new-id")
            // Routed through the dedicated create-in-folder call (not the plain create).
            assertThat(repo.lastCreateInFolder?.folderId).isEqualTo("f1")
            assertThat(repo.lastCreate).isNull()
        }

    @Test
    fun `create document at the root uses the plain create endpoint`() = runTest(dispatcher) {
        repo.createResult = ApiResult.Success(testDocument("root-doc", title = "Untitled"))

        val vm = rootViewModel()
        advanceUntilIdle()

        var createdId: String? = null
        vm.createDocument(title = "Untitled") { createdId = it }
        advanceUntilIdle()

        assertThat(createdId).isEqualTo("root-doc")
        assertThat(repo.lastCreate?.folderId).isNull()
        assertThat(repo.lastCreateInFolder).isNull()
    }

    @Test
    fun `move document delegates to the repository with the target folder`() = runTest(dispatcher) {
        val vm = rootViewModel()
        advanceUntilIdle()

        vm.moveDocument("d1", targetFolderId = "f2")
        advanceUntilIdle()

        assertThat(repo.lastMove?.id).isEqualTo("d1")
        assertThat(repo.lastMove?.folderId).isEqualTo("f2")
    }

    @Test
    fun `create folder parents under the current level`() = runTest(dispatcher) {
        val vm = folderViewModel("f1")
        advanceUntilIdle()

        vm.createFolder("Reports")
        advanceUntilIdle()

        assertThat(repo.lastFolderCreate?.name).isEqualTo("Reports")
        assertThat(repo.lastFolderCreate?.parentId).isEqualTo("f1")
    }

    @Test
    fun `blank folder name is ignored`() = runTest(dispatcher) {
        val vm = rootViewModel()
        advanceUntilIdle()

        vm.createFolder("   ")
        advanceUntilIdle()

        assertThat(repo.lastFolderCreate).isNull()
    }

    @Test
    fun `rename folder delegates the trimmed name`() = runTest(dispatcher) {
        val vm = rootViewModel()
        advanceUntilIdle()

        vm.renameFolder("f1", "  Archive  ")
        advanceUntilIdle()

        assertThat(repo.lastFolderRename?.id).isEqualTo("f1")
        assertThat(repo.lastFolderRename?.name).isEqualTo("Archive")
    }

    @Test
    fun `delete folder delegates to the repository`() = runTest(dispatcher) {
        val vm = rootViewModel()
        advanceUntilIdle()

        vm.deleteFolder("f1")
        advanceUntilIdle()

        assertThat(repo.lastDeletedFolderId).isEqualTo("f1")
    }

    @Test
    fun `search runs against the repository and stores results`() = runTest(dispatcher) {
        repo.searchResult = ApiResult.Success(listOf(testDocument("s1", title = "Match")))

        val vm = rootViewModel()
        advanceUntilIdle()

        vm.openSearch()
        vm.onSearchQueryChange("match")
        advanceUntilIdle()

        assertThat(vm.uiState.value.isSearchActive).isTrue()
        assertThat(repo.lastSearchQuery).isEqualTo("match")
        assertThat(vm.uiState.value.searchResults.single().title).isEqualTo("Match")
        assertThat(vm.uiState.value.isSearching).isFalse()
    }

    @Test
    fun `blank search query clears results without hitting the repository`() = runTest(dispatcher) {
        val vm = rootViewModel()
        advanceUntilIdle()

        vm.openSearch()
        vm.onSearchQueryChange("")
        advanceUntilIdle()

        assertThat(vm.uiState.value.searchResults).isEmpty()
        assertThat(repo.lastSearchQuery).isNull()
    }

    @Test
    fun `deleted folder route falls back to root contents`() = runTest(dispatcher) {
        // Folder "ghost" is not in the tree; contents should degrade to root.
        repo.folders.value = listOf(DocumentFolder("f1", "Work", null))
        repo.documents.value = listOf(testDocument("root1"))

        val vm = folderViewModel("ghost")
        advanceUntilIdle()

        assertThat(vm.uiState.value.contents.folderId).isEqualTo(FolderNode.ROOT_ID)
    }
}
