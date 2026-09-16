package com.interlinedlist.android.feature.lists.ui.schema

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.lists.FakeListsRepository
import com.interlinedlist.android.feature.lists.domain.FieldType
import com.interlinedlist.android.feature.lists.domain.ListDetail
import com.interlinedlist.android.feature.lists.domain.ListSchema
import com.interlinedlist.android.feature.lists.domain.ListSource
import com.interlinedlist.android.feature.lists.domain.ListSummary
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
class SchemaEditorViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private fun detail(schema: ListSchema) = ListDetail(
        summary = ListSummary("L1", "Reading", null, 0, null, false, null),
        schema = schema,
        rows = emptyList(),
    )

    /** The nine fixed GitHub issue columns, abbreviated to three. */
    private val githubSchema = ListSchema(
        listOf(
            SchemaField("number", "Issue #", FieldType.NUMBER, readOnly = true),
            SchemaField("title", "Title", FieldType.TEXT, required = true),
            SchemaField("state", "State", FieldType.SELECT, options = listOf("open", "closed")),
        ),
    )

    private fun githubDetail() = ListDetail(
        summary = ListSummary(
            id = "L1",
            title = "Repo issues",
            description = null,
            itemCount = 0,
            folderId = null,
            isPublic = false,
            updatedAt = null,
            parentId = null,
            source = ListSource.GITHUB,
            githubRepo = "octocat/Hello-World",
            githubRepoPrivate = true,
        ),
        schema = githubSchema,
        rows = emptyList(),
    )

    private fun viewModel(repo: FakeListsRepository) =
        SchemaEditorViewModel(repo, SavedStateHandle(mapOf(SCHEMA_LIST_ID_ARG to "L1")))

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `loads existing columns from the list schema`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply {
            detailResult = ApiResult.Success(
                detail(ListSchema(listOf(SchemaField("title", "Title", FieldType.TEXT)))),
            )
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.columns.map { it.key }).containsExactly("title")
    }

    @Test
    fun `add edit and remove columns update the editable state`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply { detailResult = ApiResult.Success(detail(ListSchema.EMPTY)) }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.addColumn()
        val added = vm.uiState.value.columns.single()
        vm.updateKey(added.uiId, "pages")
        vm.updateLabel(added.uiId, "Pages")
        vm.updateType(added.uiId, FieldType.NUMBER)

        val edited = vm.uiState.value.columns.single()
        assertThat(edited.key).isEqualTo("pages")
        assertThat(edited.label).isEqualTo("Pages")
        assertThat(edited.type).isEqualTo(FieldType.NUMBER)
        assertThat(vm.uiState.value.canSave).isTrue()

        vm.removeColumn(edited.uiId)
        assertThat(vm.uiState.value.columns).isEmpty()
        assertThat(vm.uiState.value.canSave).isFalse()
    }

    @Test
    fun `save sends the edited schema and flags saved`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply { detailResult = ApiResult.Success(detail(ListSchema.EMPTY)) }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.addColumn()
        val id = vm.uiState.value.columns.single().uiId
        vm.updateKey(id, "name")

        var saved = false
        vm.save { saved = true }
        advanceUntilIdle()

        assertThat(saved).isTrue()
        assertThat(vm.uiState.value.saved).isTrue()
        assertThat(repo.updateSchemaCount).isEqualTo(1)
        // Incomplete rows are dropped; only the keyed column is persisted.
        assertThat(repo.lastSchemaUpdate!!.fields.map { it.key }).containsExactly("name")
    }

    @Test
    fun `save is a no-op when no column has a key`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply { detailResult = ApiResult.Success(detail(ListSchema.EMPTY)) }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.addColumn() // key still blank
        vm.save()
        advanceUntilIdle()

        assertThat(repo.updateSchemaCount).isEqualTo(0)
        assertThat(vm.uiState.value.saved).isFalse()
    }

    @Test
    fun `save failure surfaces an error and does not flag saved`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply {
            detailResult = ApiResult.Success(detail(ListSchema.EMPTY))
            updateSchemaResult = FakeListsRepository.subscriptionFailure()
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.addColumn()
        vm.updateKey(vm.uiState.value.columns.single().uiId, "name")
        vm.save()
        advanceUntilIdle()

        assertThat(vm.uiState.value.saved).isFalse()
        assertThat(vm.uiState.value.subscriptionRequired).isTrue()
        assertThat(vm.uiState.value.errorMessage).isNotNull()
    }

    // --- GitHub-backed lists: the schema is fixed, parent-only ---------------

    @Test
    fun `a GitHub-backed list locks the schema editor`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply { detailResult = ApiResult.Success(githubDetail()) }
        val vm = viewModel(repo)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.isSchemaLocked).isTrue()
        assertThat(state.canEditColumns).isFalse()
        assertThat(state.canSave).isFalse()
        // The fixed columns are still shown, so the user can see what they get.
        assertThat(state.columns.map { it.key }).containsExactly("number", "title", "state").inOrder()
        assertThat(state.columns.first().readOnly).isTrue()
        assertThat(state.githubRepo).isEqualTo("octocat/Hello-World")
    }

    @Test
    fun `a local list leaves the schema editor unlocked`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply {
            detailResult = ApiResult.Success(
                detail(ListSchema(listOf(SchemaField("title", "Title", FieldType.TEXT)))),
            )
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.isSchemaLocked).isFalse()
        assertThat(state.canEditColumns).isTrue()
        assertThat(state.canSave).isTrue()
    }

    @Test
    fun `a locked editor refuses every column edit rather than failing at save`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply { detailResult = ApiResult.Success(githubDetail()) }
        val vm = viewModel(repo)
        advanceUntilIdle()
        val before = vm.uiState.value.columns

        vm.addColumn()
        vm.updateKey(before.first().uiId, "hijacked")
        vm.updateLabel(before.first().uiId, "Hijacked")
        vm.updateType(before.first().uiId, FieldType.BOOLEAN)
        vm.removeColumn(before.first().uiId)
        vm.save()
        advanceUntilIdle()

        assertThat(vm.uiState.value.columns).isEqualTo(before)
        // Nothing reaches the server, so there is no confusing server-side error.
        assertThat(repo.updateSchemaCount).isEqualTo(0)
        assertThat(vm.uiState.value.saved).isFalse()
    }

    @Test
    fun `the parent list is the one thing a locked editor can change`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply { detailResult = ApiResult.Success(githubDetail()) }
        repo.cache.value = listOf(
            ListSummary("L1", "Repo issues", null, 0, null, false, null),
            ListSummary("L2", "Projects", null, 0, null, false, null),
        )
        val vm = viewModel(repo)
        advanceUntilIdle()

        // A list cannot be its own parent.
        assertThat(vm.uiState.value.parentOptions.map { it.id }).containsExactly("L2")

        vm.setParent("L2")
        advanceUntilIdle()

        assertThat(repo.lastUpdatedParentId).isEqualTo("L2")
        assertThat(vm.uiState.value.parentId).isEqualTo("L2")
        // Only the parent moved; the title and visibility were not asserted.
        assertThat(repo.lastUpdatedTitle).isNull()
        assertThat(repo.lastUpdatedIsPublic).isNull()
    }
}
