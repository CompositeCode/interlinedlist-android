package com.interlinedlist.android.feature.lists.ui.connections

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.lists.FakeListsRepository
import com.interlinedlist.android.feature.lists.domain.ListConnection
import com.interlinedlist.android.feature.lists.domain.ListSummary
import com.interlinedlist.android.feature.lists.domain.Paged
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
class ConnectionsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private fun connection(id: String) = ListConnection(id, "l1", "l2", null, "L1", "L2")
    private fun summary(id: String) = ListSummary(id, "List $id", null, 0, null, false, null)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `loads connections and lists on init`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply {
            connectionsResult = ApiResult.Success(listOf(connection("c1")))
            refreshResult = ApiResult.Success(
                Paged(listOf(summary("l1"), summary("l2")), hasMore = false, total = 2, offset = 2),
            )
        }
        val vm = ConnectionsViewModel(repo)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.connections.map { it.id }).containsExactly("c1")
        assertThat(state.lists.map { it.id }).containsExactly("l1", "l2").inOrder()
        assertThat(state.canCreate).isTrue()
    }

    @Test
    fun `createConnection appends the created edge`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply {
            createConnectionResult = ApiResult.Success(connection("c-new"))
        }
        val vm = ConnectionsViewModel(repo)
        advanceUntilIdle()

        var done = false
        vm.createConnection("l1", "l2", "blocks") { done = true }
        advanceUntilIdle()

        assertThat(done).isTrue()
        assertThat(vm.uiState.value.connections.map { it.id }).contains("c-new")
        assertThat(vm.uiState.value.isSaving).isFalse()
    }

    @Test
    fun `createConnection is rejected when endpoints match`() = runTest(dispatcher) {
        val repo = FakeListsRepository()
        val vm = ConnectionsViewModel(repo)
        advanceUntilIdle()

        vm.createConnection("l1", "l1", null)
        advanceUntilIdle()

        assertThat(vm.uiState.value.connections).isEmpty()
    }

    @Test
    fun `deleteConnection removes the edge from state`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply {
            connectionsResult = ApiResult.Success(listOf(connection("c1"), connection("c2")))
        }
        val vm = ConnectionsViewModel(repo)
        advanceUntilIdle()

        vm.deleteConnection("c1")
        advanceUntilIdle()

        assertThat(vm.uiState.value.connections.map { it.id }).containsExactly("c2")
    }

    @Test
    fun `load failure surfaces an error`() = runTest(dispatcher) {
        val repo = FakeListsRepository().apply {
            connectionsResult = ApiResult.Failure(
                com.interlinedlist.android.core.common.result.AppError.Network("offline"),
            )
        }
        val vm = ConnectionsViewModel(repo)
        advanceUntilIdle()

        assertThat(vm.uiState.value.errorMessage).isNotNull()
        assertThat(vm.uiState.value.isLoading).isFalse()
    }
}
