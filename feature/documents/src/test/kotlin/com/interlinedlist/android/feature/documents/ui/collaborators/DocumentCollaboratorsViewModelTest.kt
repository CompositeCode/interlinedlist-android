package com.interlinedlist.android.feature.documents.ui.collaborators

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.documents.domain.Collaborator
import com.interlinedlist.android.feature.documents.domain.CollaboratorCandidate
import com.interlinedlist.android.feature.documents.domain.CollaboratorRole
import com.interlinedlist.android.feature.documents.ui.FakeDocumentsRepository
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
class DocumentCollaboratorsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private fun collaborator(userId: String, role: CollaboratorRole = CollaboratorRole.VIEWER) =
        Collaborator(userId, role, "Name $userId", userId, null, null)

    private fun candidate(userId: String) =
        CollaboratorCandidate(userId, "user-$userId", "Name $userId", null, null)

    private fun viewModel(repo: FakeDocumentsRepository) =
        DocumentCollaboratorsViewModel(
            repo, SavedStateHandle(mapOf(COLLABORATORS_DOCUMENT_ID_ARG to "D1")),
        )

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `loads collaborators on init`() = runTest(dispatcher) {
        val repo = FakeDocumentsRepository().apply {
            collaboratorsResult = ApiResult.Success(
                listOf(collaborator("u1", CollaboratorRole.ADMIN), collaborator("u2")),
            )
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        assertThat(vm.uiState.value.isLoading).isFalse()
        assertThat(vm.uiState.value.collaborators.map { it.userId }).containsExactly("u1", "u2").inOrder()
    }

    @Test
    fun `searchUsers passes the query and populates candidates`() = runTest(dispatcher) {
        val repo = FakeDocumentsRepository().apply {
            collaboratorsResult = ApiResult.Success(emptyList())
            searchUsersResult = ApiResult.Success(listOf(candidate("u9")))
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onSearchQueryChange("grace")
        vm.searchUsers()
        advanceUntilIdle()

        assertThat(repo.lastSearchUsersQuery).isEqualTo("grace")
        assertThat(vm.uiState.value.candidates.map { it.userId }).containsExactly("u9")
    }

    @Test
    fun `invite optimistically adds the collaborator with the chosen role`() = runTest(dispatcher) {
        val repo = FakeDocumentsRepository().apply {
            collaboratorsResult = ApiResult.Success(emptyList())
            inviteResult = ApiResult.Success(collaborator("u9", CollaboratorRole.EDITOR))
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.selectRole(CollaboratorRole.EDITOR)
        vm.invite(candidate("u9"))
        advanceUntilIdle()

        assertThat(repo.lastInvite?.userId).isEqualTo("u9")
        assertThat(repo.lastInvite?.role).isEqualTo(CollaboratorRole.EDITOR)
        assertThat(vm.uiState.value.collaborators.map { it.userId }).containsExactly("u9")
    }

    @Test
    fun `invite failure rolls back and surfaces an error`() = runTest(dispatcher) {
        val repo = FakeDocumentsRepository().apply {
            collaboratorsResult = ApiResult.Success(emptyList())
            inviteResult = ApiResult.Failure(AppError.Server("boom"))
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.invite(candidate("u9"))
        advanceUntilIdle()

        assertThat(vm.uiState.value.collaborators).isEmpty()
        assertThat(vm.uiState.value.errorMessage).isNotNull()
    }

    @Test
    fun `changeRole optimistically updates and rolls back on failure`() = runTest(dispatcher) {
        val repo = FakeDocumentsRepository().apply {
            collaboratorsResult = ApiResult.Success(listOf(collaborator("u1", CollaboratorRole.VIEWER)))
            updateRoleResult = ApiResult.Failure(AppError.Server("boom"))
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.changeRole("u1", CollaboratorRole.ADMIN)
        // Applied immediately (optimistic).
        assertThat(vm.uiState.value.collaborators.single().role).isEqualTo(CollaboratorRole.ADMIN)

        advanceUntilIdle()
        // Rolled back after the failure.
        assertThat(vm.uiState.value.collaborators.single().role).isEqualTo(CollaboratorRole.VIEWER)
        assertThat(vm.uiState.value.errorMessage).isNotNull()
    }

    @Test
    fun `revoke optimistically removes and rolls back on failure`() = runTest(dispatcher) {
        val repo = FakeDocumentsRepository().apply {
            collaboratorsResult = ApiResult.Success(listOf(collaborator("u1"), collaborator("u2")))
            removeCollaboratorResult = ApiResult.Failure(AppError.Server("boom"))
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.revoke("u2")
        assertThat(vm.uiState.value.collaborators.map { it.userId }).containsExactly("u1")

        advanceUntilIdle()
        assertThat(vm.uiState.value.collaborators.map { it.userId }).containsExactly("u1", "u2").inOrder()
        assertThat(vm.uiState.value.errorMessage).isNotNull()
    }
}
