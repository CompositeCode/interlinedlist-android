package com.interlinedlist.android.feature.lists.ui.list

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.lists.FakeGithubRepository
import com.interlinedlist.android.feature.lists.FakeListsRepository
import com.interlinedlist.android.feature.lists.domain.GithubOrg
import com.interlinedlist.android.feature.lists.domain.GithubRepo
import com.interlinedlist.android.feature.lists.domain.ListSource
import com.interlinedlist.android.feature.lists.domain.ListSummary
import com.interlinedlist.android.feature.lists.ui.github.GithubLinkProblem
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

/**
 * The "New list" sheet: a local list, or one backed by a GitHub repository.
 *
 * The GitHub half has to behave sanely for a user who has not linked GitHub — the
 * picker must explain itself rather than render an empty control.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NewListViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val helloWorld = GithubRepo(owner = "octocat", name = "Hello-World")
    private val secretPlans = GithubRepo(owner = "acme", name = "secret-plans", isPrivate = true)

    private fun viewModel(
        lists: FakeListsRepository = FakeListsRepository(),
        github: FakeGithubRepository = FakeGithubRepository(),
    ) = NewListViewModel(lists, github)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `creating a GitHub-backed list sends the repo as a github source`() = runTest(dispatcher) {
        val lists = FakeListsRepository()
        val github = FakeGithubRepository().apply {
            reposResult = ApiResult.Success(listOf(helloWorld))
        }
        val vm = viewModel(lists, github)

        vm.selectKind(NewListKind.GITHUB)
        advanceUntilIdle()
        vm.selectRepo(helloWorld)

        var created: ListSummary? = null
        vm.create { created = it }
        advanceUntilIdle()

        assertThat(lists.lastCreateSource).isEqualTo(ListSource.GITHUB)
        assertThat(lists.lastCreateGithubRepo).isEqualTo("octocat/Hello-World")
        assertThat(lists.lastCreateGithubSource).isEqualTo("issues")
        assertThat(created).isNotNull()
    }

    @Test
    fun `picking a repository defaults the title to the repository name`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.selectRepo(helloWorld)

        assertThat(vm.uiState.value.title).isEqualTo("Hello-World")
        assertThat(vm.uiState.value.canCreate).isTrue()
    }

    @Test
    fun `a title the user already typed is not overwritten by the repo name`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onTitleChange("Roadmap")
        vm.selectRepo(helloWorld)

        assertThat(vm.uiState.value.title).isEqualTo("Roadmap")
    }

    @Test
    fun `creating a local list sends no github fields`() = runTest(dispatcher) {
        val lists = FakeListsRepository()
        val vm = viewModel(lists)

        vm.onTitleChange("Books")
        vm.create { }
        advanceUntilIdle()

        assertThat(lists.lastCreateSource).isNull()
        assertThat(lists.lastCreateGithubRepo).isNull()
        assertThat(lists.lastCreateGithubSource).isNull()
    }

    @Test
    fun `nothing GitHub is fetched until the GitHub tab is opened`() = runTest(dispatcher) {
        val github = FakeGithubRepository()
        val vm = viewModel(github = github)
        advanceUntilIdle()

        assertThat(github.reposCount).isEqualTo(0)

        vm.selectKind(NewListKind.GITHUB)
        advanceUntilIdle()

        assertThat(github.reposCount).isEqualTo(1)
    }

    @Test
    fun `an unlinked GitHub account explains itself instead of showing an empty picker`() =
        runTest(dispatcher) {
            val github = FakeGithubRepository().apply {
                reposResult = ApiResult.Failure(AppError.Unknown("GitHub account not linked"))
            }
            val vm = viewModel(github = github)

            vm.selectKind(NewListKind.GITHUB)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertThat(state.linkProblem).isEqualTo(GithubLinkProblem.NOT_LINKED)
            // The panel carries the explanation, so no duplicate generic error.
            assertThat(state.errorMessage).isNull()
            assertThat(state.canCreate).isFalse()
            // "No repositories" must not be claimed when we never got a list.
            assertThat(state.hasNoRepos).isFalse()
        }

    @Test
    fun `a GitHub token without the Issues scope routes the user to reconnect`() = runTest(dispatcher) {
        val github = FakeGithubRepository().apply {
            reposResult = ApiResult.Failure(AppError.Unauthorized("Unauthorized"))
        }
        val vm = viewModel(github = github)

        vm.selectKind(NewListKind.GITHUB)
        advanceUntilIdle()

        assertThat(vm.uiState.value.linkProblem).isEqualTo(GithubLinkProblem.NEEDS_ISSUES_SCOPE)
    }

    @Test
    fun `a linked account with no reachable repositories is called out as such`() = runTest(dispatcher) {
        val vm = viewModel(github = FakeGithubRepository())

        vm.selectKind(NewListKind.GITHUB)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.linkProblem).isNull()
        assertThat(state.hasNoRepos).isTrue()
    }

    @Test
    fun `choosing an organization re-scopes the repo query`() = runTest(dispatcher) {
        val github = FakeGithubRepository().apply {
            orgsResult = ApiResult.Success(listOf(GithubOrg("acme")))
            reposResult = ApiResult.Success(listOf(helloWorld, secretPlans))
        }
        val vm = viewModel(github = github)

        vm.selectKind(NewListKind.GITHUB)
        advanceUntilIdle()
        assertThat(vm.uiState.value.orgs.map { it.login }).containsExactly("acme")
        assertThat(github.lastReposOrg).isNull()

        vm.selectOrg("acme")
        advanceUntilIdle()

        assertThat(github.lastReposOrg).isEqualTo("acme")
        // The org list is fetched once, not again per scope change.
        assertThat(github.orgsCount).isEqualTo(1)
    }

    @Test
    fun `the repo filter searches owner and name`() = runTest(dispatcher) {
        val github = FakeGithubRepository().apply {
            reposResult = ApiResult.Success(listOf(helloWorld, secretPlans))
        }
        val vm = viewModel(github = github)

        vm.selectKind(NewListKind.GITHUB)
        advanceUntilIdle()

        vm.onRepoQueryChange("acme")
        assertThat(vm.uiState.value.visibleRepos.map { it.fullName }).containsExactly("acme/secret-plans")

        vm.onRepoQueryChange("hello")
        assertThat(vm.uiState.value.visibleRepos.map { it.fullName }).containsExactly("octocat/Hello-World")
    }

    @Test
    fun `a failed create keeps the form filled and surfaces the reason`() = runTest(dispatcher) {
        val lists = FakeListsRepository().apply {
            createResult = ApiResult.Failure(AppError.SubscriptionRequired("Subscribe to create lists."))
        }
        val vm = viewModel(lists)

        vm.onTitleChange("Books")
        vm.create { }
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.title).isEqualTo("Books")
        assertThat(state.subscriptionRequired).isTrue()
        assertThat(state.errorMessage).isNotNull()
    }

    @Test
    fun `create is refused until there is something to create`() = runTest(dispatcher) {
        val lists = FakeListsRepository()
        val vm = viewModel(lists)

        // Local with no title.
        vm.create { }
        advanceUntilIdle()
        assertThat(lists.lastCreateGithubRepo).isNull()
        assertThat(vm.uiState.value.canCreate).isFalse()

        // GitHub with no repository chosen.
        vm.selectKind(NewListKind.GITHUB)
        vm.onTitleChange("Anything")
        advanceUntilIdle()
        assertThat(vm.uiState.value.canCreate).isFalse()
    }
}
