package com.interlinedlist.android.feature.profile.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.profile.domain.SettingsBounds
import com.interlinedlist.android.feature.profile.domain.UserSettings
import com.interlinedlist.android.feature.profile.domain.isPrivateAccountOrDefault
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
 * The private-account toggle (issue #34) — the Permissions group.
 *
 * Making an account private changes who can see the owner's content, so the write
 * has to behave exactly like the other preferences: its own partial PATCH, applied
 * optimistically and rolled back when the server refuses, with the screen state
 * always reporting what the server last said.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsPrivateAccountTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeSettingsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeSettingsRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** A view model already loaded with [settings]. */
    private fun loadedViewModel(settings: UserSettings): SettingsViewModel {
        repo.refreshResult = ApiResult.Success(settings)
        return SettingsViewModel(repo)
    }

    private val serverErrorMessage = "InterlinedList is having trouble right now. Try again shortly."

    @Test
    fun `going private saves only that field`() = runTest(dispatcher) {
        val vm = loadedViewModel(UserSettings(isPrivateAccount = false))

        vm.uiState.test {
            advanceUntilIdle()
            vm.setPrivateAccount(true)

            // Applied optimistically, before the request comes back.
            assertThat(vm.uiState.value.settings?.isPrivateAccount).isTrue()
            assertThat(vm.uiState.value.isSaving).isTrue()
            advanceUntilIdle()

            val sent = repo.updates.single()
            assertThat(sent.isPrivateAccount).isTrue()
            assertThat(sent.touchedFieldNames()).containsExactly("isPrivateAccount")
            val state = expectMostRecentItem()
            assertThat(state.settings?.isPrivateAccount).isTrue()
            assertThat(state.isSaving).isFalse()
            assertThat(state.errorMessage).isNull()
        }
    }

    @Test
    fun `going public again saves only that field`() = runTest(dispatcher) {
        val vm = loadedViewModel(UserSettings(isPrivateAccount = true))

        vm.uiState.test {
            advanceUntilIdle()
            vm.setPrivateAccount(false)
            advanceUntilIdle()

            val sent = repo.updates.single()
            assertThat(sent.isPrivateAccount).isFalse()
            assertThat(sent.touchedFieldNames()).containsExactly("isPrivateAccount")
            assertThat(expectMostRecentItem().settings?.isPrivateAccount).isFalse()
        }
    }

    @Test
    fun `a failed save rolls the account back to public and surfaces the error`() = runTest(dispatcher) {
        val vm = loadedViewModel(UserSettings(isPrivateAccount = false))
        repo.updateResult = { ApiResult.Failure(AppError.Server("boom")) }

        vm.uiState.test {
            advanceUntilIdle()
            vm.setPrivateAccount(true)
            assertThat(vm.uiState.value.settings?.isPrivateAccount).isTrue()
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertThat(state.settings?.isPrivateAccount).isFalse()
            assertThat(state.isSaving).isFalse()
            assertThat(state.errorMessage).isEqualTo(serverErrorMessage)
        }
    }

    @Test
    fun `a failed save on a private account leaves it private`() = runTest(dispatcher) {
        val vm = loadedViewModel(UserSettings(isPrivateAccount = true))
        repo.updateResult = { ApiResult.Failure(AppError.Network("offline")) }

        vm.uiState.test {
            advanceUntilIdle()
            vm.setPrivateAccount(false)
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertThat(state.settings?.isPrivateAccount).isTrue()
            assertThat(state.errorMessage)
                .isEqualTo("No connection. Check your network and try again.")
        }
    }

    @Test
    fun `setting the value already stored does not call the API`() = runTest(dispatcher) {
        val vm = loadedViewModel(UserSettings(isPrivateAccount = true))
        advanceUntilIdle()

        vm.setPrivateAccount(true)
        advanceUntilIdle()

        assertThat(repo.updates).isEmpty()
        assertThat(vm.uiState.value.settings?.isPrivateAccount).isTrue()
    }

    @Test
    fun `an account with no stored value reads as public and can still be made private`() =
        runTest(dispatcher) {
            // The API may omit the field; the row shows "public" for it, so turning the
            // switch off must not spend a request while turning it on must.
            val vm = loadedViewModel(UserSettings(isPrivateAccount = null))
            advanceUntilIdle()

            assertThat(vm.uiState.value.settings?.isPrivateAccountOrDefault).isFalse()
            vm.setPrivateAccount(false)
            advanceUntilIdle()
            assertThat(repo.updates).isEmpty()

            vm.setPrivateAccount(true)
            advanceUntilIdle()
            assertThat(repo.updates.single().isPrivateAccount).isTrue()
        }

    @Test
    fun `the state shows what the server last said after a refresh`() = runTest(dispatcher) {
        // The screen renders the switch from this state, so a refresh that brings back
        // a value changed elsewhere (the web, another device) must be reflected here.
        val vm = loadedViewModel(UserSettings(isPrivateAccount = false))

        vm.uiState.test {
            advanceUntilIdle()
            assertThat(vm.uiState.value.settings?.isPrivateAccountOrDefault).isFalse()

            repo.refreshResult = ApiResult.Success(UserSettings(isPrivateAccount = true))
            vm.refresh()
            advanceUntilIdle()

            assertThat(expectMostRecentItem().settings?.isPrivateAccountOrDefault).isTrue()
        }
    }

    @Test
    fun `a refresh triggered elsewhere is reflected without reloading this screen`() =
        runTest(dispatcher) {
            val vm = loadedViewModel(UserSettings(isPrivateAccount = false))

            vm.uiState.test {
                advanceUntilIdle()
                repo.refreshResult = ApiResult.Success(UserSettings(isPrivateAccount = true))
                repo.refresh()
                advanceUntilIdle()

                assertThat(expectMostRecentItem().settings?.isPrivateAccountOrDefault).isTrue()
            }
        }

    @Test
    fun `an account is public unless it says otherwise`() {
        assertThat(SettingsBounds.DEFAULT_PRIVATE_ACCOUNT).isFalse()
        assertThat(UserSettings().isPrivateAccountOrDefault).isFalse()
        assertThat(UserSettings(isPrivateAccount = true).isPrivateAccountOrDefault).isTrue()
    }
}
