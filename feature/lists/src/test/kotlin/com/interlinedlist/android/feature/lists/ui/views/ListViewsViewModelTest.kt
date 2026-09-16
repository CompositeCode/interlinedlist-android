package com.interlinedlist.android.feature.lists.ui.views

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.lists.FakeListsRepository
import com.interlinedlist.android.feature.lists.data.CurrentUserIdProvider
import com.interlinedlist.android.feature.lists.domain.ListView
import com.interlinedlist.android.feature.lists.domain.ListViewConfig
import com.interlinedlist.android.feature.lists.domain.ListViewDensity
import com.interlinedlist.android.feature.lists.domain.ListViewScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ListViewsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private fun view(
        id: String,
        name: String = "View $id",
        scope: ListViewScope = ListViewScope.PERSONAL,
        ownerId: String? = "me",
        isDefault: Boolean = false,
        config: ListViewConfig = ListViewConfig.DEFAULT,
    ) = ListView(
        id = id,
        listId = "L1",
        userId = ownerId,
        name = name,
        scope = scope,
        config = config,
        isDefault = isDefault,
        position = 0,
    )

    private fun viewModel(repo: FakeListsRepository, userId: String? = "me") =
        ListViewsViewModel(
            repository = repo,
            currentUserIdProvider = CurrentUserIdProvider { userId },
            savedStateHandle = SavedStateHandle(mapOf(VIEWS_LIST_ID_ARG to "L1")),
        )

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `loads shared and personal views and selects the default`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply {
            viewsResult = ApiResult.Success(
                listOf(
                    view("v1", "Roadmap", ListViewScope.SHARED, ownerId = "someone-else"),
                    view("v2", "Mine", ListViewScope.PERSONAL, isDefault = true),
                ),
            )
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.sharedViews.map { it.id }).containsExactly("v1")
        assertThat(state.personalViews.map { it.id }).containsExactly("v2")
        // The default view is what the screen opens on.
        assertThat(state.selectedViewId).isEqualTo("v2")
        // Someone else's shared view is read-only but forkable.
        assertThat(state.canModify(state.sharedViews.single())).isFalse()
        assertThat(state.canFork(state.sharedViews.single())).isTrue()
        assertThat(state.canModify(state.personalViews.single())).isTrue()
    }

    @Test
    fun `load failure surfaces a message`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply {
            viewsResult = ApiResult.Failure(AppError.Server("boom"))
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        assertThat(vm.uiState.value.isLoading).isFalse()
        assertThat(vm.uiState.value.errorMessage).isNotNull()
    }

    @Test
    fun `create adds the view the server returned and selects it`() = runTest(dispatcher) {
        val serverCopy = view("v-new", "By status", ListViewScope.SHARED)
        val repo = FakeListsRepository().apply { createViewResult = ApiResult.Success(serverCopy) }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.createView("By status", ListViewScope.SHARED)
        advanceUntilIdle()

        assertThat(repo.lastCreatedViewScope).isEqualTo(ListViewScope.SHARED)
        assertThat(vm.uiState.value.views.map { it.id }).containsExactly("v-new")
        assertThat(vm.uiState.value.selectedViewId).isEqualTo("v-new")
        assertThat(vm.uiState.value.isSaving).isFalse()
    }

    @Test
    fun `create without a scope reports the problem and adds nothing`() = runTest(dispatcher) {
        val repo = FakeListsRepository()
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.createView("Scopeless", scope = null)
        advanceUntilIdle()

        assertThat(vm.uiState.value.views).isEmpty()
        assertThat(vm.uiState.value.errorMessage).contains("shared or personal")
    }

    @Test
    fun `rename adopts the server copy of the view, not the optimistic one`() = runTest(dispatcher) {
        // The server silently rewrites config values it does not recognise, so what
        // it returns — a compact density here — is what the UI must end up showing.
        val stored = ListViewConfig(
            buildJsonObject {
                put("mode", JsonPrimitive("records"))
                put("density", JsonPrimitive("compact"))
                put("groupBy", JsonPrimitive("status"))
            },
        )
        val repo = FakeListsRepository().apply {
            viewsResult = ApiResult.Success(listOf(view("v1", "Old name")))
            updateViewResult = ApiResult.Success(view("v1", "New name", config = stored))
        }
        val vm = viewModel(repo)
        advanceUntilIdle()
        assertThat(vm.uiState.value.views.single().config.density).isEqualTo(ListViewDensity.COMFORTABLE)

        vm.renameView(vm.uiState.value.views.single(), "New name")
        advanceUntilIdle()

        val updated = vm.uiState.value.views.single()
        assertThat(updated.name).isEqualTo("New name")
        assertThat(updated.config.density).isEqualTo(ListViewDensity.COMPACT)
        assertThat(updated.config.raw["groupBy"]).isEqualTo(JsonPrimitive("status"))
        assertThat(repo.lastUpdatedViewName).isEqualTo("New name")
    }

    @Test
    fun `rename of someone else's shared view never reaches the server`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply {
            viewsResult = ApiResult.Success(
                listOf(view("v1", "Roadmap", ListViewScope.SHARED, ownerId = "someone-else")),
            )
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.renameView(vm.uiState.value.views.single(), "Mine now")
        advanceUntilIdle()

        assertThat(repo.lastUpdatedViewId).isNull()
        assertThat(vm.uiState.value.errorMessage).contains("personal copy")
        assertThat(vm.uiState.value.views.single().name).isEqualTo("Roadmap")
    }

    @Test
    fun `a server refusal to rename leaves the view untouched`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply {
            viewsResult = ApiResult.Success(listOf(view("v1", "Roadmap", ListViewScope.SHARED)))
            updateViewResult = ApiResult.Failure(AppError.Forbidden("You cannot modify this view"))
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.renameView(vm.uiState.value.views.single(), "Renamed")
        advanceUntilIdle()

        assertThat(vm.uiState.value.views.single().name).isEqualTo("Roadmap")
        assertThat(vm.uiState.value.errorMessage).isEqualTo("You cannot modify this view")
        assertThat(vm.uiState.value.isSaving).isFalse()
    }

    @Test
    fun `setting a default re-reads the views so the flag moves`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply {
            viewsResult = ApiResult.Success(
                listOf(view("v1", "First", isDefault = true), view("v2", "Second")),
            )
        }
        val vm = viewModel(repo)
        advanceUntilIdle()
        // The server moves the flag; the re-read reflects it across both views.
        repo.updateViewResult = ApiResult.Success(view("v2", "Second", isDefault = true))
        repo.viewsResult = ApiResult.Success(
            listOf(view("v1", "First"), view("v2", "Second", isDefault = true)),
        )

        vm.setDefault(vm.uiState.value.views.last())
        advanceUntilIdle()

        assertThat(repo.lastUpdatedViewIsDefault).isTrue()
        assertThat(vm.uiState.value.views.map { it.isDefault }).containsExactly(false, true).inOrder()
    }

    @Test
    fun `fork adds the personal copy the server made and selects it`() = runTest(dispatcher) {
        val shared = view("v1", "Roadmap", ListViewScope.SHARED, ownerId = "someone-else")
        val repo = FakeListsRepository().apply {
            viewsResult = ApiResult.Success(listOf(shared))
            forkViewResult = ApiResult.Success(
                view("v1-copy", "Roadmap (copy)", ListViewScope.PERSONAL),
            )
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.forkView(vm.uiState.value.views.single())
        advanceUntilIdle()

        assertThat(repo.lastForkedViewId).isEqualTo("v1")
        val state = vm.uiState.value
        assertThat(state.personalViews.map { it.name }).containsExactly("Roadmap (copy)")
        assertThat(state.selectedViewId).isEqualTo("v1-copy")
        // The shared original is left exactly as it was.
        assertThat(state.sharedViews).containsExactly(shared)
    }

    @Test
    fun `delete removes the view once the server confirms and reselects`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply {
            viewsResult = ApiResult.Success(
                listOf(view("v1", "First", isDefault = true), view("v2", "Second")),
            )
        }
        val vm = viewModel(repo)
        advanceUntilIdle()
        vm.selectView("v2")

        vm.deleteView(vm.uiState.value.views.last())
        advanceUntilIdle()

        assertThat(repo.lastDeletedViewId).isEqualTo("v2")
        assertThat(vm.uiState.value.views.map { it.id }).containsExactly("v1")
        assertThat(vm.uiState.value.selectedViewId).isEqualTo("v1")
    }

    @Test
    fun `a refused delete keeps the view in the list`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply {
            viewsResult = ApiResult.Success(listOf(view("v1", "Roadmap", ListViewScope.SHARED)))
            deleteViewResult = ApiResult.Failure(AppError.Forbidden("You cannot modify this view"))
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.deleteView(vm.uiState.value.views.single())
        advanceUntilIdle()

        assertThat(vm.uiState.value.views.map { it.id }).containsExactly("v1")
        assertThat(vm.uiState.value.errorMessage).isEqualTo("You cannot modify this view")
    }

    @Test
    fun `delete of someone else's shared view never reaches the server`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply {
            viewsResult = ApiResult.Success(
                listOf(view("v1", "Roadmap", ListViewScope.SHARED, ownerId = "someone-else")),
            )
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.deleteView(vm.uiState.value.views.single())
        advanceUntilIdle()

        assertThat(repo.lastDeletedViewId).isNull()
        assertThat(vm.uiState.value.views).hasSize(1)
        assertThat(vm.uiState.value.errorMessage).contains("can delete it")
    }
}
