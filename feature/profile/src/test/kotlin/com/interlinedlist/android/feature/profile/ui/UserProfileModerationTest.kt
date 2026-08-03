package com.interlinedlist.android.feature.profile.ui

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.profile.domain.ModerationStatus
import com.interlinedlist.android.feature.profile.domain.ReportReason
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

/** Moderation (block / mute / report) coverage for [UserProfileViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
class UserProfileModerationTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeProfileRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeProfileRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(username: String = "ada") =
        UserProfileViewModel(repo, SavedStateHandle(mapOf(PROFILE_USERNAME_ARG to username)))

    private fun otherUser(username: String = "ada") =
        testUser(id = "u2", username = username, isCurrentUser = false)

    @Test
    fun `loads moderation status for another user`() = runTest(dispatcher) {
        repo.refreshUserResult = ApiResult.Success(otherUser())
        repo.moderationStatusResult = ApiResult.Success(ModerationStatus(isBlocked = true, isMuted = false))

        val vm = viewModel()
        advanceUntilIdle()

        assertThat(repo.moderationStatusUsername).isEqualTo("ada")
        assertThat(vm.uiState.value.moderationStatus.isBlocked).isTrue()
        assertThat(vm.uiState.value.canModerate).isTrue()
    }

    @Test
    fun `own profile is not moderatable and skips the status call`() = runTest(dispatcher) {
        repo.refreshUserResult = ApiResult.Success(testUser(id = "u1", username = "me", isCurrentUser = true))

        val vm = viewModel("me")
        advanceUntilIdle()

        assertThat(repo.moderationStatusUsername).isNull()
        assertThat(vm.uiState.value.canModerate).isFalse()
    }

    @Test
    fun `block flips the flag optimistically and calls the repository`() = runTest(dispatcher) {
        repo.refreshUserResult = ApiResult.Success(otherUser())
        repo.moderationStatusResult = ApiResult.Success(ModerationStatus())
        val vm = viewModel()
        advanceUntilIdle()

        vm.toggleBlock()
        advanceUntilIdle()

        assertThat(repo.blockedUsername).isEqualTo("ada")
        assertThat(repo.blockCount).isEqualTo(1)
        assertThat(vm.uiState.value.moderationStatus.isBlocked).isTrue()
        assertThat(vm.uiState.value.isModerationActionInProgress).isFalse()
    }

    @Test
    fun `block rolls back the flag and surfaces an error on failure`() = runTest(dispatcher) {
        repo.refreshUserResult = ApiResult.Success(otherUser())
        repo.moderationStatusResult = ApiResult.Success(ModerationStatus())
        repo.blockResult = ApiResult.Failure(AppError.Server("boom"))
        val vm = viewModel()
        advanceUntilIdle()

        vm.toggleBlock()
        advanceUntilIdle()

        assertThat(vm.uiState.value.moderationStatus.isBlocked).isFalse()
        assertThat(vm.uiState.value.errorMessage).isNotNull()
        assertThat(vm.uiState.value.isModerationActionInProgress).isFalse()
    }

    @Test
    fun `block on an already-blocked user unblocks`() = runTest(dispatcher) {
        repo.refreshUserResult = ApiResult.Success(otherUser())
        repo.moderationStatusResult = ApiResult.Success(ModerationStatus(isBlocked = true))
        val vm = viewModel()
        advanceUntilIdle()

        vm.toggleBlock()
        advanceUntilIdle()

        assertThat(repo.unblockedUsername).isEqualTo("ada")
        assertThat(repo.blockCount).isEqualTo(0)
        assertThat(vm.uiState.value.moderationStatus.isBlocked).isFalse()
    }

    @Test
    fun `mute flips the flag optimistically and calls the repository`() = runTest(dispatcher) {
        repo.refreshUserResult = ApiResult.Success(otherUser())
        repo.moderationStatusResult = ApiResult.Success(ModerationStatus())
        val vm = viewModel()
        advanceUntilIdle()

        vm.toggleMute()
        advanceUntilIdle()

        assertThat(repo.mutedUsername).isEqualTo("ada")
        assertThat(vm.uiState.value.moderationStatus.isMuted).isTrue()
    }

    @Test
    fun `report calls the repository with reason and detail and flags success`() = runTest(dispatcher) {
        repo.refreshUserResult = ApiResult.Success(otherUser())
        repo.moderationStatusResult = ApiResult.Success(ModerationStatus())
        val vm = viewModel()
        advanceUntilIdle()

        vm.report(ReportReason.HARASSMENT, "They keep messaging me.")
        advanceUntilIdle()

        assertThat(repo.reportArgs)
            .isEqualTo(Triple("ada", ReportReason.HARASSMENT, "They keep messaging me."))
        assertThat(vm.uiState.value.reportSubmitted).isTrue()
        assertThat(vm.uiState.value.isModerationActionInProgress).isFalse()
    }

    @Test
    fun `report surfaces an error on failure`() = runTest(dispatcher) {
        repo.refreshUserResult = ApiResult.Success(otherUser())
        repo.moderationStatusResult = ApiResult.Success(ModerationStatus())
        repo.reportResult = ApiResult.Failure(AppError.Network("offline"))
        val vm = viewModel()
        advanceUntilIdle()

        vm.report(ReportReason.SPAM, null)
        advanceUntilIdle()

        assertThat(vm.uiState.value.reportSubmitted).isFalse()
        assertThat(vm.uiState.value.errorMessage).isNotNull()
    }
}
