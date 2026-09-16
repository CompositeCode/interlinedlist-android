package com.interlinedlist.android.feature.lists.ui.detail

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.lists.FakeListsRepository
import com.interlinedlist.android.feature.lists.domain.FieldType
import com.interlinedlist.android.feature.lists.domain.ListDetail
import com.interlinedlist.android.feature.lists.domain.ListRow
import com.interlinedlist.android.feature.lists.domain.ListSchema
import com.interlinedlist.android.feature.lists.domain.ListSummary
import com.interlinedlist.android.feature.lists.domain.RefreshResult
import com.interlinedlist.android.feature.lists.domain.SchemaField
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
class ListDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val schema = ListSchema(
        listOf(
            SchemaField("title", "Title", FieldType.TEXT),
            SchemaField("done", "Done", FieldType.BOOLEAN),
        ),
    )

    private fun detail(rows: List<ListRow>) = ListDetail(
        summary = ListSummary("L1", "Reading", "Books", rows.size, null, false, null),
        schema = schema,
        rows = rows,
    )

    private fun viewModel(repo: FakeListsRepository) =
        ListDetailViewModel(repo, SavedStateHandle(mapOf(LIST_ID_ARG to "L1")))

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `loads schema and rows on init`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply {
            detailResult = ApiResult.Success(detail(listOf(ListRow("r1", mapOf("title" to "Dune")))))
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.schema.fields.map { it.key }).containsExactly("title", "done").inOrder()
        assertThat(state.rows).hasSize(1)
        assertThat(state.rows.first().valueFor("title")).isEqualTo("Dune")
    }

    @Test
    fun `addRow appends the new row to state`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply {
            detailResult = ApiResult.Success(detail(emptyList()))
            addRowResult = ApiResult.Success(ListRow("r-new", mapOf("title" to "Hyperion")))
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        var done = false
        vm.addRow(mapOf("title" to "Hyperion")) { done = true }
        advanceUntilIdle()

        assertThat(done).isTrue()
        assertThat(vm.uiState.value.rows.map { it.id }).containsExactly("r-new")
        assertThat(vm.uiState.value.isSaving).isFalse()
    }

    @Test
    fun `updateRow replaces the matching row`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply {
            detailResult = ApiResult.Success(detail(listOf(ListRow("r1", mapOf("title" to "Old")))))
            updateRowResult = ApiResult.Success(ListRow("r1", mapOf("title" to "New")))
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.updateRow("r1", mapOf("title" to "New"))
        advanceUntilIdle()

        assertThat(vm.uiState.value.rows.single().valueFor("title")).isEqualTo("New")
    }

    @Test
    fun `deleteRow removes the row from state`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply {
            detailResult = ApiResult.Success(
                detail(listOf(ListRow("r1", emptyMap()), ListRow("r2", emptyMap()))),
            )
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.deleteRow("r1")
        advanceUntilIdle()

        assertThat(vm.uiState.value.rows.map { it.id }).containsExactly("r2")
    }

    @Test
    fun `editMetadata optimistically updates the summary then confirms from the server`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply {
            detailResult = ApiResult.Success(detail(emptyList()))
            updateListResult = ApiResult.Success(
                ListSummary("L1", "Reading v2", "Updated", 0, null, isPublic = true, updatedAt = null),
            )
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        var done = false
        vm.editMetadata(title = "Reading v2", description = "Updated", isPublic = true) { done = true }
        advanceUntilIdle()

        assertThat(done).isTrue()
        assertThat(repo.updateListCount).isEqualTo(1)
        val state = vm.uiState.value
        assertThat(state.summary?.title).isEqualTo("Reading v2")
        assertThat(state.summary?.description).isEqualTo("Updated")
        assertThat(state.summary?.isPublic).isTrue()
        assertThat(state.isSaving).isFalse()
        assertThat(state.isEditingMetadata).isFalse()
    }

    @Test
    fun `editMetadata rolls back the summary and surfaces the error on failure`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply {
            detailResult = ApiResult.Success(detail(emptyList()))
            updateListResult = FakeListsRepository.subscriptionFailure()
        }
        val vm = viewModel(repo)
        advanceUntilIdle()
        val original = vm.uiState.value.summary

        vm.editMetadata(title = "Broken", description = "x", isPublic = true)
        advanceUntilIdle()

        val state = vm.uiState.value
        // Rolled back to the pre-edit summary.
        assertThat(state.summary).isEqualTo(original)
        assertThat(state.summary?.title).isEqualTo("Reading")
        assertThat(state.summary?.isPublic).isFalse()
        assertThat(state.errorMessage).isNotNull()
        assertThat(state.isSaving).isFalse()
    }

    @Test
    fun `loadRow merges the freshest server copy into state`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply {
            detailResult = ApiResult.Success(detail(listOf(ListRow("r1", mapOf("title" to "Stale")))))
            getRowResult = ApiResult.Success(ListRow("r1", mapOf("title" to "Fresh")))
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.loadRow("r1")
        advanceUntilIdle()

        assertThat(vm.uiState.value.rows.single().valueFor("title")).isEqualTo("Fresh")
    }

    @Test
    fun `deleteList flags deleted and invokes callback`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply { detailResult = ApiResult.Success(detail(emptyList())) }
        val vm = viewModel(repo)
        advanceUntilIdle()

        var deleted = false
        vm.deleteList { deleted = true }
        advanceUntilIdle()

        assertThat(deleted).isTrue()
        assertThat(vm.uiState.value.deleted).isTrue()
    }

    @Test
    fun `subscription gate on load surfaces the upsell flag`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply { detailResult = FakeListsRepository.subscriptionFailure() }
        val vm = viewModel(repo)
        advanceUntilIdle()

        assertThat(vm.uiState.value.subscriptionRequired).isTrue()
        assertThat(vm.uiState.value.isLoading).isFalse()
    }

    @Test
    fun `refreshFromGithub surfaces a summary and reloads the rows`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply {
            detailResult = ApiResult.Success(detail(emptyList()))
            refreshGithubResult = ApiResult.Success(
                RefreshResult(message = null, added = 2, updated = 0, removed = 0),
            )
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        // After the refresh, the reload returns freshly-synced rows.
        repo.detailResult = ApiResult.Success(detail(listOf(ListRow("r1", mapOf("title" to "Synced")))))
        vm.refreshFromGithub()
        advanceUntilIdle()

        assertThat(repo.refreshGithubCount).isEqualTo(1)
        assertThat(vm.uiState.value.isRefreshing).isFalse()
        assertThat(vm.uiState.value.refreshMessage).isEqualTo("2 added")
        assertThat(vm.uiState.value.rows.single().valueFor("title")).isEqualTo("Synced")
    }

    @Test
    fun `refreshFromGithub failure surfaces an error and clears the spinner`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply {
            detailResult = ApiResult.Success(detail(emptyList()))
            refreshGithubResult = FakeListsRepository.subscriptionFailure()
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.refreshFromGithub()
        advanceUntilIdle()

        assertThat(vm.uiState.value.isRefreshing).isFalse()
        assertThat(vm.uiState.value.errorMessage).isNotNull()
        assertThat(vm.uiState.value.refreshMessage).isNull()
    }

    // --- Parent chain (breadcrumb) + child lists -----------------------------

    private fun child(parentId: String?) = ListDetail(
        summary = ListSummary("L1", "Reading", "Books", 0, null, false, null, parentId),
        schema = schema,
        rows = emptyList(),
    )

    @Test
    fun `resolves the breadcrumb for a child list, root first`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply {
            detailResult = ApiResult.Success(child(parentId = "P1"))
            parentChainResult = ApiResult.Success(
                listOf(
                    ListSummary("ROOT", "Root", null, 0, null, false, null, null),
                    ListSummary("P1", "Parent", null, 0, null, false, null, "ROOT"),
                ),
            )
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        assertThat(repo.lastParentChainId).isEqualTo("P1")
        assertThat(vm.uiState.value.breadcrumb.map { it.id }).containsExactly("ROOT", "P1").inOrder()
    }

    @Test
    fun `does not resolve a breadcrumb for a root list`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply { detailResult = ApiResult.Success(child(parentId = null)) }
        val vm = viewModel(repo)
        advanceUntilIdle()

        assertThat(repo.parentChainCount).isEqualTo(0)
        assertThat(vm.uiState.value.breadcrumb).isEmpty()
    }

    @Test
    fun `a failed breadcrumb leaves the list usable`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply {
            detailResult = ApiResult.Success(child(parentId = "P1"))
            parentChainResult = FakeListsRepository.subscriptionFailure()
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        assertThat(vm.uiState.value.breadcrumb).isEmpty()
        assertThat(vm.uiState.value.errorMessage).isNull()
        assertThat(vm.uiState.value.summary).isNotNull()
    }

    @Test
    fun `createChildList creates the new list under the current one`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply {
            detailResult = ApiResult.Success(child(parentId = null))
            createResult = ApiResult.Success(
                ListSummary("L2", "New list", null, 0, null, false, null, "L1"),
            )
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        var opened: String? = null
        vm.createChildList { opened = it }
        advanceUntilIdle()

        assertThat(repo.lastCreateParentId).isEqualTo("L1")
        assertThat(opened).isEqualTo("L2")
        assertThat(vm.uiState.value.isSaving).isFalse()
    }

    @Test
    fun `createChildList surfaces a failure without navigating`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply {
            detailResult = ApiResult.Success(child(parentId = null))
            createResult = FakeListsRepository.subscriptionFailure()
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        var opened: String? = null
        vm.createChildList { opened = it }
        advanceUntilIdle()

        assertThat(opened).isNull()
        assertThat(vm.uiState.value.errorMessage).isNotNull()
        assertThat(vm.uiState.value.subscriptionRequired).isTrue()
        assertThat(vm.uiState.value.isSaving).isFalse()
    }
}
