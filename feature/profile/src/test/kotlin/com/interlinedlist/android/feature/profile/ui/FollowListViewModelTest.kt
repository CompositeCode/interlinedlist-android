package com.interlinedlist.android.feature.profile.ui

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.profile.ui.follow.FOLLOW_USERNAME_ARG
import com.interlinedlist.android.feature.profile.ui.follow.FollowersViewModel
import com.interlinedlist.android.feature.profile.ui.follow.FollowingViewModel
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
class FollowListViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeProfileRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeProfileRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun followers(username: String) =
        FollowersViewModel(repo, SavedStateHandle(mapOf(FOLLOW_USERNAME_ARG to username)))

    private fun following(username: String) =
        FollowingViewModel(repo, SavedStateHandle(mapOf(FOLLOW_USERNAME_ARG to username)))

    @Test
    fun `followers vm loads the followers for the nav-arg username`() = runTest(dispatcher) {
        repo.followersResult = ApiResult.Success(listOf(testFollowUser(id = "1", username = "bob")))

        val vm = followers("ada")
        advanceUntilIdle()

        assertThat(repo.followersUsername).isEqualTo("ada")
        assertThat(vm.uiState.value.users.map { it.username }).containsExactly("bob")
        assertThat(vm.uiState.value.isLoading).isFalse()
    }

    @Test
    fun `following vm loads the following for the nav-arg username`() = runTest(dispatcher) {
        repo.followingResult = ApiResult.Success(listOf(testFollowUser(id = "2", username = "cara")))

        val vm = following("ada")
        advanceUntilIdle()

        assertThat(repo.followingUsername).isEqualTo("ada")
        assertThat(vm.uiState.value.users.map { it.username }).containsExactly("cara")
    }

    @Test
    fun `an empty list flags isEmpty`() = runTest(dispatcher) {
        repo.followersResult = ApiResult.Success(emptyList())

        val vm = followers("ada")
        advanceUntilIdle()

        assertThat(vm.uiState.value.isEmpty).isTrue()
    }

    @Test
    fun `a failure surfaces a mapped error`() = runTest(dispatcher) {
        repo.followersResult = ApiResult.Failure(AppError.Network("offline"))

        val vm = followers("ada")
        advanceUntilIdle()

        assertThat(vm.uiState.value.errorMessage).isEqualTo("No connection. Check your network and try again.")
        assertThat(vm.uiState.value.isLoading).isFalse()
    }

    @Test
    fun `missing nav arg fails fast`() {
        try {
            FollowersViewModel(repo, SavedStateHandle())
            throw AssertionError("Expected IllegalStateException for missing nav arg")
        } catch (e: IllegalStateException) {
            assertThat(e).hasMessageThat().contains(FOLLOW_USERNAME_ARG)
        }
    }
}
