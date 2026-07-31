package com.interlinedlist.android.feature.profile.ui

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.profile.domain.MutualConnections
import com.interlinedlist.android.feature.profile.domain.PublicDocumentSummary
import com.interlinedlist.android.feature.profile.domain.PublicListSummary
import com.interlinedlist.android.feature.profile.domain.PublicPost
import com.interlinedlist.android.feature.profile.ui.profile.PROFILE_USERNAME_ARG
import com.interlinedlist.android.feature.profile.ui.profile.ProfileContentTab
import com.interlinedlist.android.feature.profile.ui.profile.UserProfileViewModel
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
 * Covers the other-user profile's content tabs (Posts / Lists / Documents) and the
 * mutual-connections indicator added in Milestone L.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UserProfileContentTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeProfileRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeProfileRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(username: String) =
        UserProfileViewModel(repo, SavedStateHandle(mapOf(PROFILE_USERNAME_ARG to username)))

    @Test
    fun `loads posts for the default tab once the user resolves`() = runTest(dispatcher) {
        val ada = testUser(id = "u2", username = "ada", isCurrentUser = false)
        repo.refreshUserResult = ApiResult.Success(ada)
        repo.postsResult = ApiResult.Success(
            listOf(PublicPost(id = "m1", content = "Hello", createdAt = null)),
        )

        val vm = viewModel("ada")
        advanceUntilIdle()

        assertThat(vm.uiState.value.selectedTab).isEqualTo(ProfileContentTab.POSTS)
        assertThat(repo.postsUsername).isEqualTo("ada")
        assertThat(vm.uiState.value.content.posts.map { it.id }).containsExactly("m1")
        assertThat(vm.uiState.value.content.isLoading).isFalse()
    }

    @Test
    fun `loads mutual connections for another user`() = runTest(dispatcher) {
        val ada = testUser(id = "u2", username = "ada", isCurrentUser = false)
        repo.refreshUserResult = ApiResult.Success(ada)
        repo.mutualResult = ApiResult.Success(MutualConnections(mutualFollowers = 4, mutualFollowing = 1))

        val vm = viewModel("ada")
        advanceUntilIdle()

        assertThat(repo.mutualUserId).isEqualTo("u2")
        assertThat(vm.uiState.value.mutualConnections?.total).isEqualTo(4)
    }

    @Test
    fun `does not fetch mutual connections for your own profile`() = runTest(dispatcher) {
        val me = testUser(id = "me", username = "adron", isCurrentUser = true)
        repo.refreshUserResult = ApiResult.Success(me)

        val vm = viewModel("adron")
        advanceUntilIdle()

        assertThat(repo.mutualUserId).isNull()
        assertThat(vm.uiState.value.mutualConnections).isNull()
    }

    @Test
    fun `switching to the Lists tab loads lists lazily`() = runTest(dispatcher) {
        val ada = testUser(id = "u2", username = "ada", isCurrentUser = false)
        repo.refreshUserResult = ApiResult.Success(ada)
        repo.postsResult = ApiResult.Success(emptyList())
        repo.listsResult = ApiResult.Success(
            listOf(PublicListSummary(id = "l1", title = "Todos", description = null)),
        )

        val vm = viewModel("ada")
        advanceUntilIdle()
        // Lists tab has not been visited yet, so no lists call has happened.
        assertThat(repo.listsCount).isEqualTo(0)

        vm.selectTab(ProfileContentTab.LISTS)
        advanceUntilIdle()

        assertThat(vm.uiState.value.selectedTab).isEqualTo(ProfileContentTab.LISTS)
        assertThat(repo.listsUsername).isEqualTo("ada")
        assertThat(vm.uiState.value.content.lists.map { it.id }).containsExactly("l1")
    }

    @Test
    fun `switching to the Documents tab loads documents lazily`() = runTest(dispatcher) {
        val ada = testUser(id = "u2", username = "ada", isCurrentUser = false)
        repo.refreshUserResult = ApiResult.Success(ada)
        repo.postsResult = ApiResult.Success(emptyList())
        repo.documentsResult = ApiResult.Success(
            listOf(PublicDocumentSummary(id = "d1", title = "Notes")),
        )

        val vm = viewModel("ada")
        advanceUntilIdle()

        vm.selectTab(ProfileContentTab.DOCUMENTS)
        advanceUntilIdle()

        assertThat(vm.uiState.value.selectedTab).isEqualTo(ProfileContentTab.DOCUMENTS)
        assertThat(repo.documentsUsername).isEqualTo("ada")
        assertThat(vm.uiState.value.content.documents.map { it.id }).containsExactly("d1")
    }

    @Test
    fun `re-selecting an already-loaded tab does not refetch`() = runTest(dispatcher) {
        val ada = testUser(id = "u2", username = "ada", isCurrentUser = false)
        repo.refreshUserResult = ApiResult.Success(ada)
        repo.postsResult = ApiResult.Success(emptyList())

        val vm = viewModel("ada")
        advanceUntilIdle()
        assertThat(repo.postsCount).isEqualTo(1)

        vm.selectTab(ProfileContentTab.POSTS)
        advanceUntilIdle()

        // Posts were already loaded; re-selecting the same tab is a no-op.
        assertThat(repo.postsCount).isEqualTo(1)
    }

    @Test
    fun `a content load failure surfaces an error on the tab`() = runTest(dispatcher) {
        val ada = testUser(id = "u2", username = "ada", isCurrentUser = false)
        repo.refreshUserResult = ApiResult.Success(ada)
        repo.postsResult = ApiResult.Failure(AppError.Server("boom"))

        val vm = viewModel("ada")
        advanceUntilIdle()

        assertThat(vm.uiState.value.content.errorMessage).isNotNull()
        assertThat(vm.uiState.value.content.isLoading).isFalse()
    }

    @Test
    fun `content state transitions through loading with Turbine`() = runTest(dispatcher) {
        val ada = testUser(id = "u2", username = "ada", isCurrentUser = false)
        repo.refreshUserResult = ApiResult.Success(ada)
        repo.postsResult = ApiResult.Success(
            listOf(PublicPost(id = "m1", content = "Hi", createdAt = null)),
        )

        val vm = viewModel("ada")
        vm.uiState.test {
            // Initial emission before the user resolves.
            assertThat(awaitItem().content.posts).isEmpty()
            advanceUntilIdle()
            val settled = expectMostRecentItem()
            assertThat(settled.content.posts.map { it.id }).containsExactly("m1")
            assertThat(settled.content.isLoading).isFalse()
        }
    }
}
