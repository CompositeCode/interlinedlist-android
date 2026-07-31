package com.interlinedlist.android.feature.documents.ui.templates

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.documents.domain.DocumentTemplate
import com.interlinedlist.android.feature.documents.ui.FakeDocumentsRepository
import com.interlinedlist.android.feature.documents.ui.testDocument
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
class DocumentTemplatesViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeDocumentsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeDocumentsRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(targetFolderId: String? = null) = DocumentTemplatesViewModel(
        repo,
        SavedStateHandle(
            buildMap { targetFolderId?.let { put(TEMPLATES_TARGET_FOLDER_ARG, it) } },
        ),
    )

    @Test
    fun `initial load populates the templates list`() = runTest(dispatcher) {
        repo.templatesResult = ApiResult.Success(
            listOf(DocumentTemplate("t1", "Recipe", "Ingredients")),
        )

        val vm = viewModel()
        advanceUntilIdle()

        assertThat(vm.uiState.value.isLoading).isFalse()
        assertThat(vm.uiState.value.templates.map { it.title }).containsExactly("Recipe")
        assertThat(vm.uiState.value.canSeedDefaults).isFalse()
    }

    @Test
    fun `empty template list offers seeding the defaults`() = runTest(dispatcher) {
        repo.templatesResult = ApiResult.Success(emptyList())

        val vm = viewModel()
        advanceUntilIdle()

        assertThat(vm.uiState.value.templates).isEmpty()
        assertThat(vm.uiState.value.canSeedDefaults).isTrue()
    }

    @Test
    fun `seedDefaults seeds then populates the refreshed templates`() = runTest(dispatcher) {
        repo.templatesResult = ApiResult.Success(emptyList())
        // After seeding, the refresh returns the newly created templates.
        repo.seedTemplatesResult = ApiResult.Success(
            listOf(
                DocumentTemplate("t1", "Recipe", ""),
                DocumentTemplate("t2", "Social Media Campaign", ""),
            ),
        )

        val vm = viewModel()
        advanceUntilIdle()

        vm.uiState.test {
            // Starting point: empty, seed offered.
            assertThat(awaitItem().templates).isEmpty()

            vm.seedDefaults()

            // Seeding in flight.
            assertThat(awaitItem().isSeeding).isTrue()
            // Populated with the seeded templates.
            val done = awaitItem()
            assertThat(done.isSeeding).isFalse()
            assertThat(done.templates.map { it.title })
                .containsExactly("Recipe", "Social Media Campaign")
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(repo.seedTemplatesCount).isEqualTo(1)
    }

    @Test
    fun `seedDefaults surfaces a subscription gate on failure`() = runTest(dispatcher) {
        repo.templatesResult = ApiResult.Success(emptyList())
        repo.seedTemplatesResult = ApiResult.Failure(AppError.SubscriptionRequired("Subscribe to seed templates"))

        val vm = viewModel()
        advanceUntilIdle()

        vm.seedDefaults()
        advanceUntilIdle()

        assertThat(vm.uiState.value.isSeeding).isFalse()
        assertThat(vm.uiState.value.subscriptionRequired).isTrue()
        assertThat(vm.uiState.value.errorMessage).isEqualTo("Subscribe to seed templates")
    }

    @Test
    fun `createFromTemplate opens the new document`() = runTest(dispatcher) {
        repo.templatesResult = ApiResult.Success(listOf(DocumentTemplate("t1", "Recipe", "")))
        repo.fromTemplateResult = ApiResult.Success(testDocument("doc-from-t1", title = "Recipe"))

        val vm = viewModel(targetFolderId = "f1")
        advanceUntilIdle()

        var openedId: String? = null
        vm.createFromTemplate(DocumentTemplate("t1", "Recipe", "")) { openedId = it }
        advanceUntilIdle()

        assertThat(openedId).isEqualTo("doc-from-t1")
    }

    @Test
    fun `createFromTemplate failure surfaces an error`() = runTest(dispatcher) {
        repo.templatesResult = ApiResult.Success(listOf(DocumentTemplate("t1", "Recipe", "")))
        repo.fromTemplateResult = ApiResult.Failure(AppError.Network("offline"))

        val vm = viewModel()
        advanceUntilIdle()

        var openedId: String? = null
        vm.createFromTemplate(DocumentTemplate("t1", "Recipe", "")) { openedId = it }
        advanceUntilIdle()

        assertThat(openedId).isNull()
        assertThat(vm.uiState.value.errorMessage)
            .isEqualTo("No connection. Check your network and try again.")
    }
}
