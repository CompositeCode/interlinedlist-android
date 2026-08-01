package com.interlinedlist.android.feature.integrations.ui.github

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.integrations.domain.GitHubIssue
import com.interlinedlist.android.feature.integrations.domain.GitHubLabel
import com.interlinedlist.android.feature.integrations.domain.GitHubRepo
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

@OptIn(ExperimentalCoroutinesApi::class)
class GitHubViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeIntegrationsRepository

    private val hello = GitHubRepo(owner = "adron", name = "hello")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeIntegrationsRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `loads repos on init and clears loading`() = runTest(dispatcher) {
        repo.reposResult = ApiResult.Success(listOf(hello))

        val vm = GitHubViewModel(repo)
        advanceUntilIdle()

        assertThat(vm.uiState.value.isLoadingRepos).isFalse()
        assertThat(vm.uiState.value.repos).containsExactly(hello)
        assertThat(vm.uiState.value.notConnected).isFalse()
        assertThat(repo.reposCalls).isEqualTo(1)
    }

    @Test
    fun `not-linked failure sets the connect state rather than an error`() = runTest(dispatcher) {
        repo.reposResult = ApiResult.Failure(AppError.Unknown("GitHub account not linked"))

        val vm = GitHubViewModel(repo)
        advanceUntilIdle()

        assertThat(vm.uiState.value.notConnected).isTrue()
        assertThat(vm.uiState.value.reposError).isNull()
        assertThat(vm.uiState.value.repos).isEmpty()
    }

    @Test
    fun `a generic repos failure surfaces a mapped error message`() = runTest(dispatcher) {
        repo.reposResult = ApiResult.Failure(AppError.Network(null))

        val vm = GitHubViewModel(repo)
        advanceUntilIdle()

        assertThat(vm.uiState.value.notConnected).isFalse()
        assertThat(vm.uiState.value.reposError)
            .isEqualTo("No connection. Check your network and try again.")
    }

    @Test
    fun `empty repos is a success with no error`() = runTest(dispatcher) {
        repo.reposResult = ApiResult.Success(emptyList())

        val vm = GitHubViewModel(repo)
        advanceUntilIdle()

        assertThat(vm.uiState.value.repos).isEmpty()
        assertThat(vm.uiState.value.reposError).isNull()
        assertThat(vm.uiState.value.notConnected).isFalse()
    }

    @Test
    fun `selecting a repo loads its issues, labels and assignees`() = runTest(dispatcher) {
        repo.reposResult = ApiResult.Success(listOf(hello))
        repo.issuesByRepo["adron/hello"] = ApiResult.Success(
            listOf(GitHubIssue(number = 1, title = "First")),
        )
        repo.labelsResult = ApiResult.Success(listOf(GitHubLabel("bug")))
        val vm = GitHubViewModel(repo)
        advanceUntilIdle()

        vm.selectRepo(hello)
        advanceUntilIdle()

        assertThat(vm.uiState.value.selectedRepo).isEqualTo(hello)
        assertThat(vm.uiState.value.isLoadingIssues).isFalse()
        assertThat(vm.uiState.value.issues).hasSize(1)
        assertThat(vm.uiState.value.labels.map { it.name }).containsExactly("bug")
        assertThat(repo.issuesRequested).contains("adron/hello" to null)
    }

    @Test
    fun `selecting a repo with no issues shows an empty list without error`() = runTest(dispatcher) {
        repo.reposResult = ApiResult.Success(listOf(hello))
        repo.issuesByRepo["adron/hello"] = ApiResult.Success(emptyList())
        val vm = GitHubViewModel(repo)
        advanceUntilIdle()

        vm.selectRepo(hello)
        advanceUntilIdle()

        assertThat(vm.uiState.value.issues).isEmpty()
        assertThat(vm.uiState.value.issuesError).isNull()
    }

    @Test
    fun `creating an issue optimistically prepends it and confirms`() = runTest(dispatcher) {
        repo.reposResult = ApiResult.Success(listOf(hello))
        repo.issuesByRepo["adron/hello"] = ApiResult.Success(
            listOf(GitHubIssue(number = 1, title = "Old")),
        )
        repo.createIssueResult = ApiResult.Success(GitHubIssue(number = 2, title = "New"))
        val vm = GitHubViewModel(repo)
        advanceUntilIdle()
        vm.selectRepo(hello)
        advanceUntilIdle()

        vm.createIssue(title = "New", body = "b", labels = listOf("bug"), assignees = listOf("adron"))
        advanceUntilIdle()

        assertThat(vm.uiState.value.isCreatingIssue).isFalse()
        // New issue is at the head of the list.
        assertThat(vm.uiState.value.issues.map { it.number }).containsExactly(2, 1).inOrder()
        assertThat(vm.uiState.value.message).isEqualTo("Issue created")
        // The right repo/title/labels/assignees were sent.
        val created = repo.createdIssues.single()
        assertThat(created.repo).isEqualTo("adron/hello")
        assertThat(created.title).isEqualTo("New")
        assertThat(created.labels).containsExactly("bug")
        assertThat(created.assignees).containsExactly("adron")
    }

    @Test
    fun `a blank title does not call the repository`() = runTest(dispatcher) {
        repo.reposResult = ApiResult.Success(listOf(hello))
        val vm = GitHubViewModel(repo)
        advanceUntilIdle()
        vm.selectRepo(hello)
        advanceUntilIdle()

        vm.createIssue(title = "   ", body = null)
        advanceUntilIdle()

        assertThat(repo.createdIssues).isEmpty()
    }

    @Test
    fun `a failed create surfaces a mapped error and leaves the list unchanged`() = runTest(dispatcher) {
        repo.reposResult = ApiResult.Success(listOf(hello))
        repo.issuesByRepo["adron/hello"] = ApiResult.Success(
            listOf(GitHubIssue(number = 1, title = "Old")),
        )
        repo.createIssueResult = ApiResult.Failure(AppError.Server(null))
        val vm = GitHubViewModel(repo)
        advanceUntilIdle()
        vm.selectRepo(hello)
        advanceUntilIdle()

        vm.createIssue(title = "New", body = null)
        advanceUntilIdle()

        assertThat(vm.uiState.value.createError)
            .isEqualTo("InterlinedList is having trouble right now. Try again shortly.")
        assertThat(vm.uiState.value.issues.map { it.number }).containsExactly(1)
    }

    @Test
    fun `adding a comment sends it and confirms`() = runTest(dispatcher) {
        repo.reposResult = ApiResult.Success(listOf(hello))
        repo.issuesByRepo["adron/hello"] = ApiResult.Success(
            listOf(GitHubIssue(number = 5, title = "Bug")),
        )
        repo.addCommentResult = ApiResult.Success(Unit)
        val vm = GitHubViewModel(repo)
        advanceUntilIdle()
        vm.selectRepo(hello)
        advanceUntilIdle()

        vm.addComment(GitHubIssue(number = 5, title = "Bug"), "Looking into it")
        advanceUntilIdle()

        assertThat(vm.uiState.value.message).isEqualTo("Comment added")
        val added = repo.addedComments.single()
        assertThat(added.owner).isEqualTo("adron")
        assertThat(added.repo).isEqualTo("hello")
        assertThat(added.number).isEqualTo(5)
        assertThat(added.body).isEqualTo("Looking into it")
    }

    @Test
    fun `state transitions expose the in-flight create flag`() = runTest(dispatcher) {
        repo.reposResult = ApiResult.Success(listOf(hello))
        repo.issuesByRepo["adron/hello"] = ApiResult.Success(emptyList())
        repo.createIssueResult = ApiResult.Success(GitHubIssue(number = 9, title = "X"))
        val vm = GitHubViewModel(repo)
        advanceUntilIdle()
        vm.selectRepo(hello)
        advanceUntilIdle()

        vm.uiState.test {
            assertThat(awaitItem().isCreatingIssue).isFalse() // current
            vm.createIssue(title = "X", body = null)
            assertThat(awaitItem().isCreatingIssue).isTrue() // in-flight
            advanceUntilIdle()
            assertThat(awaitItem().isCreatingIssue).isFalse() // done
            cancelAndIgnoreRemainingEvents()
        }
    }
}
