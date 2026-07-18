package com.interlinedlist.android.feature.integrations.ui.export

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.integrations.domain.ExportType
import com.interlinedlist.android.feature.integrations.ui.FakeIntegrationsRepository
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
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ExportViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeIntegrationsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeIntegrationsRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `successful export clears loading and emits a ready event`() = runTest(dispatcher) {
        val file = File.createTempFile("lists", ".csv").apply { deleteOnExit() }
        repo.exportResult = ApiResult.Success(file)
        val vm = ExportViewModel(repo)

        vm.ready.test {
            vm.export(ExportType.LISTS)
            advanceUntilIdle()

            assertThat(awaitItem().file).isEqualTo(file)
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(repo.exportedTypes).containsExactly(ExportType.LISTS)
        assertThat(vm.uiState.value.downloading).isNull()
        assertThat(vm.uiState.value.errorMessage).isNull()
    }

    @Test
    fun `download shows a per-type spinner while in flight`() = runTest(dispatcher) {
        repo.exportResult = ApiResult.Success(File.createTempFile("msg", ".csv").apply { deleteOnExit() })
        val vm = ExportViewModel(repo)

        vm.uiState.test {
            assertThat(awaitItem().downloading).isNull() // initial

            vm.export(ExportType.MESSAGES)
            assertThat(awaitItem().isDownloading(ExportType.MESSAGES)).isTrue() // in-flight

            advanceUntilIdle()
            assertThat(awaitItem().downloading).isNull() // done
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a second export is ignored while one is in flight`() = runTest(dispatcher) {
        repo.exportResult = ApiResult.Success(File.createTempFile("fol", ".csv").apply { deleteOnExit() })
        val vm = ExportViewModel(repo)

        vm.export(ExportType.FOLLOWS)
        vm.export(ExportType.LISTS) // dropped: busy
        advanceUntilIdle()

        assertThat(repo.exportedTypes).containsExactly(ExportType.FOLLOWS)
    }

    @Test
    fun `failed export surfaces a mapped error and emits no ready event`() = runTest(dispatcher) {
        repo.exportResult = ApiResult.Failure(AppError.Network(null))
        val vm = ExportViewModel(repo)

        vm.export(ExportType.LIST_DATA_ROWS)
        advanceUntilIdle()

        assertThat(vm.uiState.value.downloading).isNull()
        assertThat(vm.uiState.value.errorMessage)
            .isEqualTo("No connection. Check your network and try again.")
    }
}
