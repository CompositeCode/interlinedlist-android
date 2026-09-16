package com.interlinedlist.android.feature.profile.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.core.datastore.ThemeMode
import com.interlinedlist.android.feature.profile.domain.UserSettings
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

/**
 * Issue #36: the theme control on the Settings screen.
 *
 * Two things set it apart from the other preferences on this screen and are pinned
 * here: it reads from the **device's** store rather than from the account field the
 * settings carry, and a failed save does **not** roll it back — the app has already
 * re-themed and the choice is owed to the account, not lost.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsThemeTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeSettingsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeSettingsRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun loadedViewModel(
        settings: UserSettings = UserSettings(),
        storedTheme: ThemeMode = ThemeMode.SYSTEM,
    ): SettingsViewModel {
        repo.refreshResult = ApiResult.Success(settings)
        repo.seedThemeMode(storedTheme)
        return SettingsViewModel(repo)
    }

    private val serverErrorMessage = "InterlinedList is having trouble right now. Try again shortly."

    @Test
    fun `the control shows the theme stored on the device`() = runTest(dispatcher) {
        val vm = loadedViewModel(storedTheme = ThemeMode.DARK)
        advanceUntilIdle()

        assertThat(vm.uiState.value.themeMode).isEqualTo(ThemeMode.DARK)
    }

    /**
     * The device's store wins over the account field even when the two disagree: the
     * account may simply not have caught up with a change made offline.
     */
    @Test
    fun `the control follows the device store rather than the account field`() =
        runTest(dispatcher) {
            val vm = loadedViewModel(
                settings = UserSettings(theme = "light"),
                storedTheme = ThemeMode.DARK,
            )
            advanceUntilIdle()

            assertThat(vm.uiState.value.themeMode).isEqualTo(ThemeMode.DARK)
        }

    @Test
    fun `choosing a theme saves it and shows it at once`() = runTest(dispatcher) {
        val vm = loadedViewModel(storedTheme = ThemeMode.SYSTEM)

        vm.uiState.test {
            advanceUntilIdle()
            vm.setThemeMode(ThemeMode.DARK)

            // The save is marked pending without waiting for the dispatcher.
            assertThat(vm.uiState.value.isSaving).isTrue()
            advanceUntilIdle()

            assertThat(repo.themeModes).containsExactly(ThemeMode.DARK)
            // The theme goes out on its own path, not mixed into a settings PATCH.
            assertThat(repo.updates).isEmpty()
            val state = expectMostRecentItem()
            assertThat(state.themeMode).isEqualTo(ThemeMode.DARK)
            assertThat(state.isSaving).isFalse()
            assertThat(state.errorMessage).isNull()
        }
    }

    @Test
    fun `re-choosing the current theme does not call the repository`() = runTest(dispatcher) {
        val vm = loadedViewModel(storedTheme = ThemeMode.LIGHT)
        advanceUntilIdle()

        vm.setThemeMode(ThemeMode.LIGHT)
        advanceUntilIdle()

        assertThat(repo.themeModes).isEmpty()
    }

    /**
     * The offline case as the user sees it: the app has re-themed, the failure is
     * reported, and the selection stays where they put it.
     */
    @Test
    fun `a failed save reports the error but keeps the chosen theme`() = runTest(dispatcher) {
        val vm = loadedViewModel(storedTheme = ThemeMode.LIGHT)
        repo.themeResult = { ApiResult.Failure(AppError.Server("boom")) }

        vm.uiState.test {
            advanceUntilIdle()
            vm.setThemeMode(ThemeMode.DARK)
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertThat(state.themeMode).isEqualTo(ThemeMode.DARK)
            assertThat(state.isSaving).isFalse()
            assertThat(state.errorMessage)
                .isEqualTo("$serverErrorMessage ${SettingsViewModel.THEME_NOT_SYNCED_NOTE}")
        }
    }

    @Test
    fun `a theme adopted from the account while the screen is open moves the selection`() =
        runTest(dispatcher) {
            val vm = loadedViewModel(storedTheme = ThemeMode.SYSTEM)
            advanceUntilIdle()

            // The reconciliation that follows a refresh writes the account's choice to
            // the device store; the screen follows it.
            repo.seedThemeMode(ThemeMode.DARK)
            advanceUntilIdle()

            assertThat(vm.uiState.value.themeMode).isEqualTo(ThemeMode.DARK)
        }
}
