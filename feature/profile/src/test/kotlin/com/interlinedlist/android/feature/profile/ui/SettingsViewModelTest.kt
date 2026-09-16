package com.interlinedlist.android.feature.profile.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.profile.domain.UserSettings
import com.interlinedlist.android.feature.profile.domain.ViewingPreference
import com.interlinedlist.android.feature.profile.ui.settings.SettingsViewModel
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
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeSettingsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeSettingsRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `load surfaces the fetched settings and clears loading`() = runTest(dispatcher) {
        repo.refreshResult = ApiResult.Success(
            UserSettings(viewingPreference = ViewingPreference.FOLLOWING, showPreviews = false),
        )
        val vm = SettingsViewModel(repo)

        vm.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertThat(repo.refreshCount).isEqualTo(1)
            assertThat(state.isLoading).isFalse()
            assertThat(state.settings?.viewingPreference).isEqualTo(ViewingPreference.FOLLOWING)
            assertThat(state.settings?.showPreviews).isFalse()
            assertThat(state.errorMessage).isNull()
        }
    }

    @Test
    fun `load failure surfaces a mapped error and no settings`() = runTest(dispatcher) {
        repo.refreshResult = ApiResult.Failure(AppError.Network("offline"))
        val vm = SettingsViewModel(repo)

        vm.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertThat(state.isLoading).isFalse()
            assertThat(state.settings).isNull()
            assertThat(state.errorMessage).isEqualTo("No connection. Check your network and try again.")
        }
    }

    @Test
    fun `retry after a failure reloads`() = runTest(dispatcher) {
        repo.refreshResult = ApiResult.Failure(AppError.Network("offline"))
        val vm = SettingsViewModel(repo)

        vm.uiState.test {
            advanceUntilIdle()
            repo.refreshResult = ApiResult.Success(UserSettings(showPreviews = false))
            vm.refresh()
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertThat(repo.refreshCount).isEqualTo(2)
            assertThat(state.errorMessage).isNull()
            assertThat(state.settings?.showPreviews).isFalse()
        }
    }

    @Test
    fun `choosing a viewing preference saves only that field`() = runTest(dispatcher) {
        repo.refreshResult = ApiResult.Success(UserSettings(viewingPreference = ViewingPreference.ALL))
        val vm = SettingsViewModel(repo)

        vm.uiState.test {
            advanceUntilIdle()
            vm.setViewingPreference(ViewingPreference.FOLLOWERS)
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertThat(state.settings?.viewingPreference).isEqualTo(ViewingPreference.FOLLOWERS)
            assertThat(state.isSaving).isFalse()
            assertThat(repo.updates).hasSize(1)
            val sent = repo.updates.single()
            assertThat(sent.viewingPreference).isEqualTo(ViewingPreference.FOLLOWERS)
            assertThat(sent.showPreviews).isNull()
            assertThat(sent.displayName).isNull()
            assertThat(sent.theme).isNull()
        }
    }

    @Test
    fun `re-choosing the selected viewing preference does not call the API`() = runTest(dispatcher) {
        repo.refreshResult = ApiResult.Success(UserSettings(viewingPreference = ViewingPreference.MINE))
        val vm = SettingsViewModel(repo)

        vm.uiState.test {
            advanceUntilIdle()
            vm.setViewingPreference(ViewingPreference.MINE)
            advanceUntilIdle()

            assertThat(repo.updates).isEmpty()
            assertThat(expectMostRecentItem().settings?.viewingPreference)
                .isEqualTo(ViewingPreference.MINE)
        }
    }

    @Test
    fun `toggling link previews saves only that field`() = runTest(dispatcher) {
        repo.refreshResult = ApiResult.Success(UserSettings(showPreviews = true))
        val vm = SettingsViewModel(repo)

        vm.uiState.test {
            advanceUntilIdle()
            vm.setShowPreviews(false)
            advanceUntilIdle()

            assertThat(expectMostRecentItem().settings?.showPreviews).isFalse()
            val sent = repo.updates.single()
            assertThat(sent.showPreviews).isFalse()
            assertThat(sent.viewingPreference).isNull()
        }
    }

    @Test
    fun `a failed save rolls the value back and surfaces the error`() = runTest(dispatcher) {
        repo.refreshResult = ApiResult.Success(UserSettings(showPreviews = true))
        repo.updateResult = { ApiResult.Failure(AppError.Server("boom")) }
        val vm = SettingsViewModel(repo)

        vm.uiState.test {
            advanceUntilIdle()
            vm.setShowPreviews(false)
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertThat(state.settings?.showPreviews).isTrue()
            assertThat(state.isSaving).isFalse()
            assertThat(state.errorMessage)
                .isEqualTo("InterlinedList is having trouble right now. Try again shortly.")
        }
    }

    @Test
    fun `dismissing the error clears it`() = runTest(dispatcher) {
        repo.refreshResult = ApiResult.Failure(AppError.Network("offline"))
        val vm = SettingsViewModel(repo)

        vm.uiState.test {
            advanceUntilIdle()
            vm.dismissError()
            advanceUntilIdle()

            assertThat(expectMostRecentItem().errorMessage).isNull()
        }
    }

    @Test
    fun `settings published by the repository are reflected without a reload`() = runTest(dispatcher) {
        repo.refreshResult = ApiResult.Success(UserSettings(viewingPreference = ViewingPreference.ALL))
        val vm = SettingsViewModel(repo)

        vm.uiState.test {
            advanceUntilIdle()
            // Another surface (e.g. the feed) refreshes the shared repository...
            repo.refreshResult = ApiResult.Success(UserSettings(viewingPreference = ViewingPreference.MINE))
            repo.refresh()
            advanceUntilIdle()

            // ...and this screen follows along.
            assertThat(expectMostRecentItem().settings?.viewingPreference)
                .isEqualTo(ViewingPreference.MINE)
        }
    }
}
