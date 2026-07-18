package com.interlinedlist.android.feature.documents.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.documents.domain.DocumentFolder
import com.interlinedlist.android.feature.documents.domain.Pagination
import com.interlinedlist.android.feature.documents.ui.index.DocumentsViewModel
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
class DocumentsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeDocumentsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeDocumentsRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `emits cached documents from the room flow`() = runTest(dispatcher) {
        repo.refreshResult = ApiResult.Success(Pagination.single(1))
        repo.rootDocuments.value = listOf(testDocument("1"), testDocument("2"))

        val vm = DocumentsViewModel(repo)
        advanceUntilIdle()

        assertThat(vm.uiState.value.documents.map { it.id }).containsExactly("1", "2")
        assertThat(vm.uiState.value.isLoading).isFalse()
    }

    @Test
    fun `refresh failure surfaces a mapped error`() = runTest(dispatcher) {
        repo.refreshResult = ApiResult.Failure(AppError.Network("offline"))

        val vm = DocumentsViewModel(repo)
        advanceUntilIdle()

        assertThat(vm.uiState.value.errorMessage).isEqualTo("No connection. Check your network and try again.")
        assertThat(vm.uiState.value.subscriptionRequired).isFalse()
    }

    @Test
    fun `subscription gate is flagged on a subscription-required failure`() = runTest(dispatcher) {
        repo.refreshResult = ApiResult.Failure(AppError.SubscriptionRequired("Subscribe to use documents"))

        val vm = DocumentsViewModel(repo)
        advanceUntilIdle()

        assertThat(vm.uiState.value.subscriptionRequired).isTrue()
        assertThat(vm.uiState.value.errorMessage).isEqualTo("Subscribe to use documents")
    }

    @Test
    fun `selecting a folder switches the observed source and refreshes it`() = runTest(dispatcher) {
        repo.refreshResult = ApiResult.Success(Pagination.single(0))
        repo.rootDocuments.value = listOf(testDocument("root"))
        repo.folderDocuments.value = listOf(testDocument("infolder", folderId = "f1"))

        val vm = DocumentsViewModel(repo)
        advanceUntilIdle()
        assertThat(vm.uiState.value.documents.map { it.id }).containsExactly("root")

        vm.selectFolder("f1")
        advanceUntilIdle()

        assertThat(vm.uiState.value.selectedFolderId).isEqualTo("f1")
        assertThat(vm.uiState.value.documents.map { it.id }).containsExactly("infolder")
        assertThat(repo.lastSelectedFolderId).isEqualTo("f1")
    }

    @Test
    fun `hasMore drives load-more which appends the next page`() = runTest(dispatcher) {
        repo.refreshResult = ApiResult.Success(Pagination(total = 40, limit = 20, offset = 0, hasMore = true))
        repo.loadMoreResult = ApiResult.Success(Pagination(total = 40, limit = 20, offset = 20, hasMore = false))

        val vm = DocumentsViewModel(repo)
        advanceUntilIdle()
        assertThat(vm.uiState.value.hasMore).isTrue()

        vm.loadMore()
        advanceUntilIdle()

        assertThat(repo.loadMoreCount).isEqualTo(1)
        assertThat(vm.uiState.value.hasMore).isFalse()
        assertThat(vm.uiState.value.isLoadingMore).isFalse()
    }

    @Test
    fun `load-more is skipped when there is no next page`() = runTest(dispatcher) {
        repo.refreshResult = ApiResult.Success(Pagination.single(2))

        val vm = DocumentsViewModel(repo)
        advanceUntilIdle()

        vm.loadMore()
        advanceUntilIdle()

        assertThat(repo.loadMoreCount).isEqualTo(0)
    }

    @Test
    fun `create document invokes onCreated with the new id`() = runTest(dispatcher) {
        repo.refreshResult = ApiResult.Success(Pagination.single(0))
        repo.createResult = ApiResult.Success(testDocument("new-id", title = "Untitled"))

        val vm = DocumentsViewModel(repo)
        advanceUntilIdle()

        var createdId: String? = null
        vm.createDocument(title = "Untitled") { createdId = it }
        advanceUntilIdle()

        assertThat(createdId).isEqualTo("new-id")
        assertThat(repo.lastCreateTitle).isEqualTo("Untitled")
    }

    @Test
    fun `folders flow is reflected in state via Turbine`() = runTest(dispatcher) {
        repo.refreshResult = ApiResult.Success(Pagination.single(0))
        val vm = DocumentsViewModel(repo)
        advanceUntilIdle()

        vm.uiState.test {
            assertThat(awaitItem().folders).isEmpty()
            repo.foldersFlow.value = listOf(DocumentFolder("f1", "Work", null))
            assertThat(awaitItem().folders.single().name).isEqualTo("Work")
        }
    }
}
