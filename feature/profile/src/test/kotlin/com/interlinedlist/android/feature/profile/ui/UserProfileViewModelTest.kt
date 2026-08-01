package com.interlinedlist.android.feature.profile.ui

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.profile.domain.FollowCounts
import com.interlinedlist.android.feature.profile.domain.FollowStatus
import com.interlinedlist.android.feature.profile.ui.profile.PROFILE_USERNAME_ARG
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

@OptIn(ExperimentalCoroutinesApi::class)
class UserProfileViewModelTest {

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
    fun `loads the user named by the nav arg`() = runTest(dispatcher) {
        val ada = testUser(id = "u2", username = "ada", displayName = "Ada Lovelace", isCurrentUser = false)
        repo.refreshUserResult = ApiResult.Success(ada)
        repo.userFlow.value = ada

        val vm = viewModel("ada")
        advanceUntilIdle()

        assertThat(vm.uiState.value.user?.username).isEqualTo("ada")
        assertThat(vm.uiState.value.user?.isCurrentUser).isFalse()
        assertThat(vm.uiState.value.isLoading).isFalse()
    }

    @Test
    fun `a missing user surfaces a not-found message`() = runTest(dispatcher) {
        repo.refreshUserResult = ApiResult.Failure(AppError.NotFound("No such user"))

        val vm = viewModel("ghost")
        advanceUntilIdle()

        assertThat(vm.uiState.value.errorMessage).isEqualTo("No such user")
        assertThat(vm.uiState.value.user).isNull()
    }

    @Test
    fun `missing nav arg fails fast`() {
        try {
            UserProfileViewModel(repo, SavedStateHandle())
            throw AssertionError("Expected IllegalStateException for missing nav arg")
        } catch (e: IllegalStateException) {
            assertThat(e).hasMessageThat().contains(PROFILE_USERNAME_ARG)
        }
    }

    @Test
    fun `loads follow status and counts for another user`() = runTest(dispatcher) {
        val ada = testUser(id = "u2", username = "ada", isCurrentUser = false)
        repo.refreshUserResult = ApiResult.Success(ada)
        repo.followStatusResult = ApiResult.Success(FollowStatus.NOT_FOLLOWING)
        repo.followCountsResult = ApiResult.Success(FollowCounts(followers = 5, following = 3))

        val vm = viewModel("ada")
        advanceUntilIdle()

        assertThat(repo.followStatusUserId).isEqualTo("u2")
        assertThat(repo.followCountsUserId).isEqualTo("u2")
        assertThat(vm.uiState.value.followStatus).isEqualTo(FollowStatus.NOT_FOLLOWING)
        assertThat(vm.uiState.value.followCounts.followers).isEqualTo(5)
        assertThat(vm.uiState.value.canFollow).isTrue()
    }

    @Test
    fun `viewing your own profile marks the status as SELF`() = runTest(dispatcher) {
        val me = testUser(id = "me", username = "adron", isCurrentUser = true)
        repo.refreshUserResult = ApiResult.Success(me)

        val vm = viewModel("adron")
        advanceUntilIdle()

        assertThat(vm.uiState.value.followStatus).isEqualTo(FollowStatus.SELF)
        assertThat(vm.uiState.value.canFollow).isFalse()
        // No status call is made for your own profile.
        assertThat(repo.followStatusUserId).isNull()
    }

    @Test
    fun `toggle follow follows a not-followed user and re-reads the status`() = runTest(dispatcher) {
        val ada = testUser(id = "u2", username = "ada", isCurrentUser = false)
        repo.refreshUserResult = ApiResult.Success(ada)
        repo.followStatusResult = ApiResult.Success(FollowStatus.NOT_FOLLOWING)
        val vm = viewModel("ada")
        advanceUntilIdle()

        // After following, the status endpoint reports FOLLOWING.
        repo.followStatusResult = ApiResult.Success(FollowStatus.FOLLOWING)
        vm.toggleFollow()
        advanceUntilIdle()

        assertThat(repo.followCount).isEqualTo(1)
        assertThat(repo.followedUserId).isEqualTo("u2")
        assertThat(vm.uiState.value.followStatus).isEqualTo(FollowStatus.FOLLOWING)
        assertThat(vm.uiState.value.isFollowActionInProgress).isFalse()
    }

    @Test
    fun `toggle follow unfollows a followed user`() = runTest(dispatcher) {
        val ada = testUser(id = "u2", username = "ada", isCurrentUser = false)
        repo.refreshUserResult = ApiResult.Success(ada)
        repo.followStatusResult = ApiResult.Success(FollowStatus.FOLLOWING)
        val vm = viewModel("ada")
        advanceUntilIdle()

        repo.followStatusResult = ApiResult.Success(FollowStatus.NOT_FOLLOWING)
        vm.toggleFollow()
        advanceUntilIdle()

        assertThat(repo.unfollowCount).isEqualTo(1)
        assertThat(repo.unfollowedUserId).isEqualTo("u2")
        assertThat(vm.uiState.value.followStatus).isEqualTo(FollowStatus.NOT_FOLLOWING)
    }

    @Test
    fun `toggle follow surfaces an error and leaves the status unchanged`() = runTest(dispatcher) {
        val ada = testUser(id = "u2", username = "ada", isCurrentUser = false)
        repo.refreshUserResult = ApiResult.Success(ada)
        repo.followStatusResult = ApiResult.Success(FollowStatus.NOT_FOLLOWING)
        repo.followResult = ApiResult.Failure(AppError.Server("boom"))
        val vm = viewModel("ada")
        advanceUntilIdle()

        vm.toggleFollow()
        advanceUntilIdle()

        assertThat(vm.uiState.value.errorMessage).isNotNull()
        assertThat(vm.uiState.value.followStatus).isEqualTo(FollowStatus.NOT_FOLLOWING)
        assertThat(vm.uiState.value.isFollowActionInProgress).isFalse()
    }

    @Test
    fun `toggle follow is a no-op on your own profile`() = runTest(dispatcher) {
        val me = testUser(id = "me", username = "adron", isCurrentUser = true)
        repo.refreshUserResult = ApiResult.Success(me)
        val vm = viewModel("adron")
        advanceUntilIdle()

        vm.toggleFollow()
        advanceUntilIdle()

        assertThat(repo.followCount).isEqualTo(0)
        assertThat(repo.unfollowCount).isEqualTo(0)
    }
}
