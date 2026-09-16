package com.interlinedlist.android.feature.documents.ui

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.core.materialize.domain.MaterializeSource
import com.interlinedlist.android.core.materialize.domain.MaterializeTarget
import com.interlinedlist.android.feature.documents.data.SaveOutcome
import com.interlinedlist.android.feature.documents.ui.editor.DOCUMENT_ID_ARG
import com.interlinedlist.android.feature.documents.ui.editor.DocumentEditorViewModel
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
class DocumentEditorViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeDocumentsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeDocumentsRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(id: String = "d1") =
        DocumentEditorViewModel(repo, SavedStateHandle(mapOf(DOCUMENT_ID_ARG to id)))

    @Test
    fun `loads the document body from the refresh result`() = runTest(dispatcher) {
        repo.refreshDocumentResult = ApiResult.Success(
            testDocument("d1", title = "Notes", content = "# Body"),
        )

        val vm = viewModel()
        advanceUntilIdle()

        assertThat(vm.uiState.value.title).isEqualTo("Notes")
        assertThat(vm.uiState.value.content).isEqualTo("# Body")
        assertThat(vm.uiState.value.isLoading).isFalse()
    }

    @Test
    fun `editing marks unsaved changes and enables save`() = runTest(dispatcher) {
        repo.refreshDocumentResult = ApiResult.Success(testDocument("d1", content = "orig"))
        val vm = viewModel()
        advanceUntilIdle()
        assertThat(vm.uiState.value.canSave).isFalse()

        vm.onContentChange("edited body")

        assertThat(vm.uiState.value.hasUnsavedChanges).isTrue()
        assertThat(vm.uiState.value.canSave).isTrue()
    }

    @Test
    fun `save persists edits via PATCH and clears the unsaved flag`() = runTest(dispatcher) {
        repo.refreshDocumentResult = ApiResult.Success(testDocument("d1", title = "T", content = "orig"))
        repo.patchOutcome = SaveOutcome.Success(testDocument("d1", title = "T", content = "edited"))
        val vm = viewModel()
        advanceUntilIdle()

        vm.onContentChange("edited")
        var saved = false
        vm.save { saved = true }
        advanceUntilIdle()

        assertThat(saved).isTrue()
        assertThat(vm.uiState.value.hasUnsavedChanges).isFalse()
        assertThat(repo.lastPatch?.content).isEqualTo("edited")
    }

    @Test
    fun `save failure surfaces an error and keeps the unsaved flag`() = runTest(dispatcher) {
        repo.refreshDocumentResult = ApiResult.Success(testDocument("d1", content = "orig"))
        repo.patchOutcome = SaveOutcome.Error("InterlinedList is having trouble right now. Try again shortly.")
        val vm = viewModel()
        advanceUntilIdle()

        vm.onContentChange("edited")
        vm.save()
        advanceUntilIdle()

        assertThat(vm.uiState.value.errorMessage).isEqualTo("InterlinedList is having trouble right now. Try again shortly.")
        assertThat(vm.uiState.value.hasUnsavedChanges).isTrue()
    }

    @Test
    fun `save conflict surfaces a conflict state and keeps the unsaved edit`() = runTest(dispatcher) {
        repo.refreshDocumentResult = ApiResult.Success(testDocument("d1", content = "orig"))
        repo.patchOutcome = SaveOutcome.Conflict("Modified elsewhere")
        val vm = viewModel()
        advanceUntilIdle()

        vm.onContentChange("my edit")
        vm.save()
        advanceUntilIdle()

        assertThat(vm.uiState.value.hasConflict).isTrue()
        assertThat(vm.uiState.value.hasUnsavedChanges).isTrue()
        assertThat(vm.uiState.value.isSaving).isFalse()
    }

    @Test
    fun `reloadForConflict pulls the latest and clears the conflict and local edits`() =
        runTest(dispatcher) {
            repo.refreshDocumentResult = ApiResult.Success(testDocument("d1", content = "orig"))
            repo.patchOutcome = SaveOutcome.Conflict("Modified elsewhere")
            val vm = viewModel()
            advanceUntilIdle()
            vm.onContentChange("my edit")
            vm.save()
            advanceUntilIdle()
            assertThat(vm.uiState.value.hasConflict).isTrue()

            repo.refreshDocumentResult = ApiResult.Success(testDocument("d1", content = "server latest"))
            vm.reloadForConflict()
            advanceUntilIdle()

            assertThat(vm.uiState.value.hasConflict).isFalse()
            assertThat(vm.uiState.value.hasUnsavedChanges).isFalse()
            assertThat(vm.uiState.value.content).isEqualTo("server latest")
        }

    @Test
    fun `save queued shows an offline hint but keeps the doc editable`() = runTest(dispatcher) {
        repo.refreshDocumentResult = ApiResult.Success(testDocument("d1", content = "orig"))
        repo.patchOutcome = SaveOutcome.Queued
        val vm = viewModel()
        advanceUntilIdle()

        vm.onContentChange("edited offline")
        var saved = false
        vm.save { saved = true }
        advanceUntilIdle()

        // Queued counts as a local save: unsaved flag clears, onSaved fires, offline hint set.
        assertThat(saved).isTrue()
        assertThat(vm.uiState.value.isQueuedOffline).isTrue()
        assertThat(vm.uiState.value.hasUnsavedChanges).isFalse()
    }

    @Test
    fun `delete invokes onDeleted on success`() = runTest(dispatcher) {
        repo.refreshDocumentResult = ApiResult.Success(testDocument("d1"))
        repo.deleteResult = ApiResult.Success(Unit)
        val vm = viewModel()
        advanceUntilIdle()

        var deleted = false
        vm.delete { deleted = true }
        advanceUntilIdle()

        assertThat(deleted).isTrue()
    }

    @Test
    fun `toggle preview flips the preview flag`() = runTest(dispatcher) {
        repo.refreshDocumentResult = ApiResult.Success(testDocument("d1"))
        val vm = viewModel()
        advanceUntilIdle()

        assertThat(vm.uiState.value.isPreview).isFalse()
        vm.togglePreview()
        assertThat(vm.uiState.value.isPreview).isTrue()
    }

    @Test
    fun `uploadImage appends a markdown reference and marks unsaved changes`() = runTest(dispatcher) {
        repo.refreshDocumentResult = ApiResult.Success(testDocument("d1", content = "start"))
        repo.uploadResult = ApiResult.Success(Unit)
        val vm = viewModel()
        advanceUntilIdle()

        vm.uploadImage(fileName = "photo.png", mimeType = "image/png", bytes = byteArrayOf(1, 2))
        advanceUntilIdle()

        assertThat(vm.uiState.value.content).contains("photo.png")
        assertThat(vm.uiState.value.hasUnsavedChanges).isTrue()
        assertThat(vm.uiState.value.isUploadingImage).isFalse()
    }

    @Test
    fun `uploadImage surfaces an error on failure`() = runTest(dispatcher) {
        repo.refreshDocumentResult = ApiResult.Success(testDocument("d1", content = "start"))
        repo.uploadResult = ApiResult.Failure(AppError.Server("boom"))
        val vm = viewModel()
        advanceUntilIdle()

        vm.uploadImage(fileName = "photo.png", mimeType = "image/png", bytes = byteArrayOf(1))
        advanceUntilIdle()

        assertThat(vm.uiState.value.errorMessage)
            .isEqualTo("InterlinedList is having trouble right now. Try again shortly.")
        assertThat(vm.uiState.value.isUploadingImage).isFalse()
    }

    // --- "Create from…" in the editor ---------------------------------------

    @Test
    fun `create from the editor opens the window on an id-only document source`() =
        runTest(dispatcher) {
            repo.refreshDocumentResult = ApiResult.Success(
                testDocument("d1", title = "Launch plan", content = "# Launch plan\n- Ship it"),
            )
            val vm = viewModel()
            advanceUntilIdle()

            vm.createFrom(MaterializeTarget.DOC)

            val launch = requireNotNull(vm.uiState.value.createFrom)
            assertThat(launch.source).isEqualTo(MaterializeSource.Document("d1"))
            assertThat(launch.initialTarget).isEqualTo(MaterializeTarget.DOC)
            assertThat(launch.preview.suggestedTitle).isEqualTo("Copy of Launch plan")
            assertThat(launch.preview.rows.map { it.values["text"] })
                .containsExactly("Launch plan", "Ship it").inOrder()
        }

    @Test
    fun `a highlighted selection opens the window on a docElements source`() = runTest(dispatcher) {
        repo.refreshDocumentResult = ApiResult.Success(
            testDocument("d1", title = "Launch plan", content = "# Launch plan\n\n## Week one\n- Ship it"),
        )
        val vm = viewModel()
        advanceUntilIdle()

        vm.createFromSelection("## Week one\n- Ship it", MaterializeTarget.LIST)

        val launch = requireNotNull(vm.uiState.value.createFrom)
        assertThat(launch.source)
            .isEqualTo(MaterializeSource.DocumentSelection("d1", "## Week one\n- Ship it"))
        assertThat(launch.source.kind).isEqualTo("docElements")
        assertThat(launch.preview.suggestedTitle).isEqualTo("Launch plan (selection)")
        // Only what was highlighted is previewed.
        assertThat(launch.preview.rows.map { it.values["text"] })
            .containsExactly("Week one", "Ship it").inOrder()
    }

    @Test
    fun `a blank selection has nothing to convert`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.createFromSelection("   \n  ", MaterializeTarget.LIST)

        assertThat(vm.uiState.value.createFrom).isNull()
    }

    @Test
    fun `dismissing the create-from window clears it`() = runTest(dispatcher) {
        repo.refreshDocumentResult = ApiResult.Success(testDocument("d1", content = "- Ship it"))
        val vm = viewModel()
        advanceUntilIdle()
        vm.createFrom(MaterializeTarget.LIST)

        vm.dismissCreateFrom()

        assertThat(vm.uiState.value.createFrom).isNull()
    }

    @Test
    fun `refresh does not overwrite in-progress edits`() = runTest(dispatcher) {
        repo.refreshDocumentResult = ApiResult.Success(testDocument("d1", content = "server"))
        val vm = viewModel()
        advanceUntilIdle()

        vm.onContentChange("my local edit")
        vm.refresh()
        advanceUntilIdle()

        assertThat(vm.uiState.value.content).isEqualTo("my local edit")
    }
}
