package com.interlinedlist.android.feature.profile.ui

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.profile.ui.edit.EditProfileViewModel
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
class EditProfileViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeProfileRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeProfileRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `seeds the form from the current user`() = runTest(dispatcher) {
        val user = testUser(displayName = "Adron Hall", bio = "Building things.")
        repo.refreshCurrentUserResult = ApiResult.Success(user)
        repo.currentUserFlow.value = user

        val vm = EditProfileViewModel(repo)
        advanceUntilIdle()

        assertThat(vm.uiState.value.displayName).isEqualTo("Adron Hall")
        assertThat(vm.uiState.value.bio).isEqualTo("Building things.")
        assertThat(vm.uiState.value.isLoading).isFalse()
    }

    @Test
    fun `editing marks unsaved changes and enables save`() = runTest(dispatcher) {
        repo.refreshCurrentUserResult = ApiResult.Success(testUser())
        val vm = EditProfileViewModel(repo)
        advanceUntilIdle()

        vm.onDisplayNameChange("New Name")

        assertThat(vm.uiState.value.hasUnsavedChanges).isTrue()
        assertThat(vm.uiState.value.canSave).isTrue()
    }

    @Test
    fun `save sends trimmed fields and invokes onSaved`() = runTest(dispatcher) {
        repo.refreshCurrentUserResult = ApiResult.Success(testUser())
        repo.updateResult = ApiResult.Success(testUser(displayName = "New Name", bio = "New bio"))
        val vm = EditProfileViewModel(repo)
        advanceUntilIdle()

        vm.onDisplayNameChange("  New Name  ")
        vm.onBioChange("  New bio  ")
        var saved = false
        vm.save { saved = true }
        advanceUntilIdle()

        assertThat(repo.lastUpdate).isEqualTo("New Name" to "New bio")
        assertThat(saved).isTrue()
        assertThat(vm.uiState.value.hasUnsavedChanges).isFalse()
    }

    @Test
    fun `save failure surfaces an error and keeps unsaved changes`() = runTest(dispatcher) {
        repo.refreshCurrentUserResult = ApiResult.Success(testUser())
        repo.updateResult = ApiResult.Failure(AppError.Server("boom"))
        val vm = EditProfileViewModel(repo)
        advanceUntilIdle()

        vm.onBioChange("changed")
        var saved = false
        vm.save { saved = true }
        advanceUntilIdle()

        assertThat(saved).isFalse()
        assertThat(vm.uiState.value.errorMessage).isEqualTo("InterlinedList is having trouble right now. Try again shortly.")
        assertThat(vm.uiState.value.hasUnsavedChanges).isTrue()
    }

    @Test
    fun `set avatar from url passes the typed url and updates the avatar`() = runTest(dispatcher) {
        repo.refreshCurrentUserResult = ApiResult.Success(testUser())
        repo.avatarFromUrlResult = ApiResult.Success(testUser(avatarUrl = "https://cdn/new.png"))
        val vm = EditProfileViewModel(repo)
        advanceUntilIdle()

        vm.onAvatarUrlInputChange("https://cdn/new.png")
        vm.setAvatarFromUrl()
        advanceUntilIdle()

        assertThat(repo.lastAvatarUrl).isEqualTo("https://cdn/new.png")
        assertThat(vm.uiState.value.avatarUrl).isEqualTo("https://cdn/new.png")
        assertThat(vm.uiState.value.avatarUrlInput).isEmpty()
    }

    @Test
    fun `upload avatar forwards the bytes and metadata`() = runTest(dispatcher) {
        repo.refreshCurrentUserResult = ApiResult.Success(testUser())
        repo.uploadAvatarResult = ApiResult.Success(testUser(avatarUrl = "https://cdn/up.png"))
        val vm = EditProfileViewModel(repo)
        advanceUntilIdle()

        vm.uploadAvatar(byteArrayOf(1, 2, 3), "avatar.png", "image/png")
        advanceUntilIdle()

        assertThat(repo.lastUpload).isEqualTo(FakeProfileRepository.Upload("avatar.png", "image/png", 3))
        assertThat(vm.uiState.value.avatarUrl).isEqualTo("https://cdn/up.png")
        assertThat(vm.uiState.value.isUploadingAvatar).isFalse()
    }

    @Test
    fun `refresh does not clobber in-progress edits`() = runTest(dispatcher) {
        // The refresh completes after the user has already typed.
        repo.refreshCurrentUserResult = ApiResult.Success(testUser(displayName = "Server Name"))
        val vm = EditProfileViewModel(repo)
        vm.onDisplayNameChange("My Draft")
        advanceUntilIdle()

        assertThat(vm.uiState.value.displayName).isEqualTo("My Draft")
    }
}
