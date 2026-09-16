package com.interlinedlist.android.feature.lists.ui.detail

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.lists.FakeGithubRepository
import com.interlinedlist.android.feature.lists.FakeListsRepository
import com.interlinedlist.android.feature.lists.domain.FieldType
import com.interlinedlist.android.feature.lists.domain.ListDetail
import com.interlinedlist.android.feature.lists.domain.ListFreshness
import com.interlinedlist.android.feature.lists.domain.ListPresence
import com.interlinedlist.android.feature.lists.domain.ListRow
import com.interlinedlist.android.feature.lists.domain.ListSchema
import com.interlinedlist.android.feature.lists.domain.ListSummary
import com.interlinedlist.android.feature.lists.domain.SchemaField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The collaborative freshness poll / presence heartbeat on the list detail screen.
 *
 * It is a long-running loop, so these tests drive virtual time explicitly with
 * [runCurrent]/[advanceTimeBy] and always stop the heartbeat before the test ends —
 * never `advanceUntilIdle()`, which would never settle while the loop is running.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ListDetailHeartbeatTest {

    private val dispatcher = StandardTestDispatcher()

    private val schema = ListSchema(
        listOf(
            SchemaField("title", "Title", FieldType.TEXT),
            SchemaField("status", "Status", FieldType.TEXT),
        ),
    )

    private val rows = listOf(
        ListRow("r1", mapOf("title" to "Dune", "status" to "in review"), version = 4),
        ListRow("r2", mapOf("title" to "Hyperion", "status" to "open"), version = 7),
    )

    private fun repoWithRows(rows: List<ListRow> = this.rows) = FakeListsRepository().apply {
        detailResult = ApiResult.Success(
            ListDetail(
                summary = ListSummary("L1", "Reading", null, rows.size, null, false, null),
                schema = schema,
                rows = rows,
            ),
        )
    }

    private fun viewModel(repo: FakeListsRepository) =
        ListDetailViewModel(repo, FakeGithubRepository(), SavedStateHandle(mapOf(LIST_ID_ARG to "L1")))

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `starting the heartbeat polls and keeps beating while the list is open`() = runTest(dispatcher) {
        val repo = repoWithRows().apply {
            freshnessResult = ApiResult.Success(ListFreshness(collaborative = true))
        }
        val vm = viewModel(repo)
        runCurrent()

        vm.startHeartbeat()
        runCurrent()
        assertThat(repo.pollCount).isEqualTo(1)

        // Nothing moved, so the loop backs off to the idle interval.
        advanceTimeBy(ListDetailViewModel.ACTIVE_INTERVAL_MS + 100)
        runCurrent()
        assertThat(repo.pollCount).isEqualTo(1)

        advanceTimeBy(ListDetailViewModel.IDLE_INTERVAL_MS)
        runCurrent()
        assertThat(repo.pollCount).isEqualTo(2)

        vm.stopHeartbeat()
    }

    @Test
    fun `a beat that brings news keeps the fast interval`() = runTest(dispatcher) {
        val repo = repoWithRows().apply {
            freshnessResults = mutableListOf(
                ApiResult.Success(
                    ListFreshness(
                        changed = listOf(ListRow("r1", mapOf("status" to "shipped"), version = 5)),
                        collaborative = true,
                    ),
                ),
            )
            freshnessResult = ApiResult.Success(ListFreshness(collaborative = true))
        }
        val vm = viewModel(repo)
        runCurrent()

        vm.startHeartbeat()
        runCurrent()
        assertThat(repo.pollCount).isEqualTo(1)

        advanceTimeBy(ListDetailViewModel.ACTIVE_INTERVAL_MS + 100)
        runCurrent()
        assertThat(repo.pollCount).isEqualTo(2)

        vm.stopHeartbeat()
    }

    @Test
    fun `stopping the heartbeat ends the polling and clears presence`() = runTest(dispatcher) {
        val repo = repoWithRows().apply {
            freshnessResult = ApiResult.Success(
                ListFreshness(
                    presence = listOf(ListPresence("u2", displayName = "Casey", username = "casey")),
                    collaborative = true,
                ),
            )
        }
        val vm = viewModel(repo)
        runCurrent()

        vm.startHeartbeat()
        runCurrent()
        assertThat(vm.uiState.value.presence).hasSize(1)
        val afterStart = repo.pollCount

        vm.stopHeartbeat()
        runCurrent()

        assertThat(vm.uiState.value.presence).isEmpty()
        advanceTimeBy(ListDetailViewModel.IDLE_INTERVAL_MS * 5)
        runCurrent()
        assertThat(repo.pollCount).isEqualTo(afterStart)
    }

    @Test
    fun `the heartbeat is idempotent - starting twice does not double the beats`() = runTest(dispatcher) {
        val repo = repoWithRows().apply {
            freshnessResult = ApiResult.Success(ListFreshness(collaborative = true))
        }
        val vm = viewModel(repo)
        runCurrent()

        vm.startHeartbeat()
        vm.startHeartbeat()
        runCurrent()

        assertThat(repo.pollCount).isEqualTo(1)
        vm.stopHeartbeat()
    }

    @Test
    fun `the poll sends the focused row and the versions of the rows on screen`() = runTest(dispatcher) {
        val repo = repoWithRows().apply {
            freshnessResult = ApiResult.Success(ListFreshness(collaborative = true))
        }
        val vm = viewModel(repo)
        runCurrent()

        vm.setFocusedRow("r2")
        vm.startHeartbeat()
        runCurrent()

        assertThat(repo.lastPolledFocusedRowId).isEqualTo("r2")
        assertThat(repo.lastPolledRowVersions).containsExactly("r1", 4, "r2", 7)

        // Leaving the row clears it again on the next beat.
        vm.setFocusedRow(null)
        advanceTimeBy(ListDetailViewModel.IDLE_INTERVAL_MS + 100)
        runCurrent()
        assertThat(repo.lastPolledFocusedRowId).isNull()

        vm.stopHeartbeat()
    }

    @Test
    fun `a row with no known version is not asked about`() = runTest(dispatcher) {
        val repo = repoWithRows(
            listOf(
                ListRow("r1", mapOf("title" to "Dune"), version = 4),
                ListRow("r2", mapOf("title" to "Hyperion")),
            ),
        ).apply { freshnessResult = ApiResult.Success(ListFreshness(collaborative = true)) }
        val vm = viewModel(repo)
        runCurrent()

        vm.startHeartbeat()
        runCurrent()

        assertThat(repo.lastPolledRowVersions).containsExactly("r1", 4)
        vm.stopHeartbeat()
    }

    @Test
    fun `changed rows are repainted without refetching the table`() = runTest(dispatcher) {
        val repo = repoWithRows().apply {
            freshnessResult = ApiResult.Success(
                ListFreshness(
                    changed = listOf(
                        ListRow("r2", mapOf("title" to "Hyperion", "status" to "blocked"), version = 9),
                    ),
                    collaborative = true,
                ),
            )
        }
        val vm = viewModel(repo)
        runCurrent()
        val detailCallsBefore = repo.detailCount

        vm.startHeartbeat()
        runCurrent()

        val state = vm.uiState.value
        assertThat(state.rows.map { it.id }).containsExactly("r1", "r2").inOrder()
        // Only the changed row moved; the untouched one is the same instance.
        assertThat(state.rows[0]).isEqualTo(rows[0])
        assertThat(state.rows[1].valueFor("status")).isEqualTo("blocked")
        assertThat(state.rows[1].version).isEqualTo(9)
        // The whole table was NOT refetched — that is the point of the endpoint.
        assertThat(repo.detailCount).isEqualTo(detailCallsBefore)

        vm.stopHeartbeat()
    }

    @Test
    fun `deleted rows are dropped from the table`() = runTest(dispatcher) {
        val repo = repoWithRows().apply {
            freshnessResult = ApiResult.Success(
                ListFreshness(deletedRowIds = listOf("r1"), collaborative = true),
            )
        }
        val vm = viewModel(repo)
        runCurrent()
        val detailCallsBefore = repo.detailCount

        vm.startHeartbeat()
        runCurrent()

        assertThat(vm.uiState.value.rows.map { it.id }).containsExactly("r2")
        assertThat(repo.detailCount).isEqualTo(detailCallsBefore)

        vm.stopHeartbeat()
    }

    @Test
    fun `presence from the poll is exposed to the screen`() = runTest(dispatcher) {
        val repo = repoWithRows().apply {
            freshnessResult = ApiResult.Success(
                ListFreshness(
                    presence = listOf(
                        ListPresence("u2", displayName = "Casey", username = "casey", focusedRowId = "r2"),
                    ),
                    collaborative = true,
                ),
            )
        }
        val vm = viewModel(repo)
        runCurrent()

        vm.startHeartbeat()
        runCurrent()

        val presence = vm.uiState.value.presence
        assertThat(presence.map { it.userId }).containsExactly("u2")
        assertThat(presence.first().focusedRowId).isEqualTo("r2")
        assertThat(vm.uiState.value.isCollaborative).isTrue()

        vm.stopHeartbeat()
    }

    @Test
    fun `a list nobody else can see is polled once and then left alone`() = runTest(dispatcher) {
        val repo = repoWithRows().apply {
            freshnessResult = ApiResult.Success(ListFreshness(collaborative = false))
        }
        val vm = viewModel(repo)
        runCurrent()

        vm.startHeartbeat()
        runCurrent()
        assertThat(repo.pollCount).isEqualTo(1)
        assertThat(vm.uiState.value.isCollaborative).isFalse()

        advanceTimeBy(ListDetailViewModel.IDLE_INTERVAL_MS * 10)
        runCurrent()

        assertThat(repo.pollCount).isEqualTo(1)
    }

    @Test
    fun `a failed beat backs off instead of retrying hard`() = runTest(dispatcher) {
        val repo = repoWithRows().apply {
            freshnessResult = ApiResult.Failure(AppError.Network("offline"))
        }
        val vm = viewModel(repo)
        runCurrent()

        vm.startHeartbeat()
        runCurrent()
        assertThat(repo.pollCount).isEqualTo(1)

        advanceTimeBy(ListDetailViewModel.ACTIVE_INTERVAL_MS + 100)
        runCurrent()
        assertThat(repo.pollCount).isEqualTo(1)

        advanceTimeBy(ListDetailViewModel.IDLE_INTERVAL_MS)
        runCurrent()
        assertThat(repo.pollCount).isEqualTo(2)

        vm.stopHeartbeat()
    }

    @Test
    fun `polling stops after ten quiet minutes and an edit revives it`() = runTest(dispatcher) {
        val repo = repoWithRows().apply {
            freshnessResult = ApiResult.Success(ListFreshness(collaborative = true))
        }
        val vm = viewModel(repo)
        runCurrent()

        vm.startHeartbeat()
        runCurrent()
        advanceTimeBy(ListDetailViewModel.MAX_IDLE_MS + ListDetailViewModel.IDLE_INTERVAL_MS)
        runCurrent()
        val quiesced = repo.pollCount

        advanceTimeBy(ListDetailViewModel.IDLE_INTERVAL_MS * 5)
        runCurrent()
        assertThat(repo.pollCount).isEqualTo(quiesced)

        // Touching a row starts it up again, without the screen doing anything.
        vm.setFocusedRow("r1")
        runCurrent()
        assertThat(repo.pollCount).isEqualTo(quiesced + 1)

        vm.stopHeartbeat()
    }

    @Test
    fun `a non-collaborative list is not revived by interaction`() = runTest(dispatcher) {
        val repo = repoWithRows().apply {
            freshnessResult = ApiResult.Success(ListFreshness(collaborative = false))
        }
        val vm = viewModel(repo)
        runCurrent()

        vm.startHeartbeat()
        runCurrent()
        assertThat(repo.pollCount).isEqualTo(1)

        vm.setFocusedRow("r1")
        vm.deleteRow("r2")
        runCurrent()

        assertThat(repo.pollCount).isEqualTo(1)
        vm.stopHeartbeat()
    }

    @Test
    fun `nothing beats again after the screen is left, even if an edit lands`() = runTest(dispatcher) {
        val repo = repoWithRows().apply {
            freshnessResult = ApiResult.Success(ListFreshness(collaborative = true))
        }
        val vm = viewModel(repo)
        runCurrent()

        vm.startHeartbeat()
        runCurrent()
        val afterStart = repo.pollCount

        vm.stopHeartbeat()
        vm.setFocusedRow("r1")
        vm.addRow(mapOf("title" to "Ubik"))
        advanceTimeBy(ListDetailViewModel.IDLE_INTERVAL_MS * 3)
        runCurrent()

        assertThat(repo.pollCount).isEqualTo(afterStart)
    }
}
