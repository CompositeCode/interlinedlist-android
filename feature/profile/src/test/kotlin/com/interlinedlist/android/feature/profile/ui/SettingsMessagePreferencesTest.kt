package com.interlinedlist.android.feature.profile.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.profile.domain.SettingsBounds
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
 * The Message settings group (issue #32): default visibility, the character limit,
 * the feed page size and the advanced-post-settings toggle.
 *
 * Each preference must go out as its own partial PATCH, apply optimistically and roll
 * back when the server refuses; the two numeric ones must additionally refuse
 * out-of-range input locally, without spending a request on it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsMessagePreferencesTest {

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

    private fun rejectSaves() {
        repo.updateResult = { ApiResult.Failure(AppError.Server("boom")) }
    }

    private val serverErrorMessage = "InterlinedList is having trouble right now. Try again shortly."

    // --- defaultPubliclyVisible ----------------------------------------------

    @Test
    fun `turning off default visibility saves only that field`() = runTest(dispatcher) {
        val vm = loadedViewModel(UserSettings(defaultPubliclyVisible = true))

        vm.uiState.test {
            advanceUntilIdle()
            vm.setDefaultPubliclyVisible(false)

            // Applied optimistically, before the request comes back.
            assertThat(vm.uiState.value.settings?.defaultPubliclyVisible).isFalse()
            assertThat(vm.uiState.value.isSaving).isTrue()
            advanceUntilIdle()

            val sent = repo.updates.single()
            assertThat(sent.defaultPubliclyVisible).isFalse()
            assertThat(sent.touchedFieldNames()).containsExactly("defaultPubliclyVisible")
            val state = expectMostRecentItem()
            assertThat(state.settings?.defaultPubliclyVisible).isFalse()
            assertThat(state.isSaving).isFalse()
            assertThat(state.errorMessage).isNull()
        }
    }

    @Test
    fun `a failed default visibility save rolls back and surfaces the error`() = runTest(dispatcher) {
        val vm = loadedViewModel(UserSettings(defaultPubliclyVisible = true))
        rejectSaves()

        vm.uiState.test {
            advanceUntilIdle()
            vm.setDefaultPubliclyVisible(false)
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertThat(state.settings?.defaultPubliclyVisible).isTrue()
            assertThat(state.isSaving).isFalse()
            assertThat(state.errorMessage).isEqualTo(serverErrorMessage)
        }
    }

    @Test
    fun `setting default visibility to the value already stored does not call the API`() =
        runTest(dispatcher) {
            val vm = loadedViewModel(UserSettings(defaultPubliclyVisible = true))
            advanceUntilIdle()

            vm.setDefaultPubliclyVisible(true)
            advanceUntilIdle()

            assertThat(repo.updates).isEmpty()
            assertThat(vm.uiState.value.settings?.defaultPubliclyVisible).isTrue()
        }

    // --- showAdvancedPostSettings --------------------------------------------

    @Test
    fun `turning on advanced post settings saves only that field`() = runTest(dispatcher) {
        val vm = loadedViewModel(UserSettings(showAdvancedPostSettings = false))

        vm.uiState.test {
            advanceUntilIdle()
            vm.setShowAdvancedPostSettings(true)

            assertThat(vm.uiState.value.settings?.showAdvancedPostSettings).isTrue()
            advanceUntilIdle()

            val sent = repo.updates.single()
            assertThat(sent.showAdvancedPostSettings).isTrue()
            assertThat(sent.touchedFieldNames()).containsExactly("showAdvancedPostSettings")
            assertThat(expectMostRecentItem().settings?.showAdvancedPostSettings).isTrue()
        }
    }

    @Test
    fun `a failed advanced post settings save rolls back and surfaces the error`() =
        runTest(dispatcher) {
            val vm = loadedViewModel(UserSettings(showAdvancedPostSettings = false))
            rejectSaves()

            vm.uiState.test {
                advanceUntilIdle()
                vm.setShowAdvancedPostSettings(true)
                advanceUntilIdle()

                val state = expectMostRecentItem()
                assertThat(state.settings?.showAdvancedPostSettings).isFalse()
                assertThat(state.errorMessage).isEqualTo(serverErrorMessage)
            }
        }

    // --- maxMessageLength ----------------------------------------------------

    @Test
    fun `setting the character limit saves only that field`() = runTest(dispatcher) {
        val vm = loadedViewModel(UserSettings(maxMessageLength = 666))

        vm.uiState.test {
            advanceUntilIdle()
            vm.setMaxMessageLength(1_000)

            assertThat(vm.uiState.value.settings?.maxMessageLength).isEqualTo(1_000)
            advanceUntilIdle()

            val sent = repo.updates.single()
            assertThat(sent.maxMessageLength).isEqualTo(1_000)
            assertThat(sent.touchedFieldNames()).containsExactly("maxMessageLength")
            assertThat(expectMostRecentItem().settings?.maxMessageLength).isEqualTo(1_000)
        }
    }

    @Test
    fun `a character limit below the minimum is refused without a request`() = runTest(dispatcher) {
        val vm = loadedViewModel(UserSettings(maxMessageLength = 666))

        vm.uiState.test {
            advanceUntilIdle()
            vm.setMaxMessageLength(SettingsBounds.MAX_MESSAGE_LENGTH.first - 1)
            advanceUntilIdle()

            assertThat(repo.updates).isEmpty()
            val state = expectMostRecentItem()
            assertThat(state.settings?.maxMessageLength).isEqualTo(666)
            assertThat(state.isSaving).isFalse()
            assertThat(state.errorMessage).isEqualTo(
                "Message character limit must be between " +
                    "${SettingsBounds.MAX_MESSAGE_LENGTH.first} and " +
                    "${SettingsBounds.MAX_MESSAGE_LENGTH.last}.",
            )
        }
    }

    @Test
    fun `a character limit above the maximum is refused without a request`() = runTest(dispatcher) {
        val vm = loadedViewModel(UserSettings(maxMessageLength = 666))

        vm.uiState.test {
            advanceUntilIdle()
            vm.setMaxMessageLength(SettingsBounds.MAX_MESSAGE_LENGTH.last + 1)
            advanceUntilIdle()

            assertThat(repo.updates).isEmpty()
            assertThat(expectMostRecentItem().settings?.maxMessageLength).isEqualTo(666)
        }
    }

    @Test
    fun `a server rejection restores the previous character limit`() = runTest(dispatcher) {
        val vm = loadedViewModel(UserSettings(maxMessageLength = 666))
        // In range for us, refused by the server (its own cap is not published).
        // A 400 normalises to AppError.Unknown carrying the server's own wording.
        repo.updateResult = { ApiResult.Failure(AppError.Unknown("maxMessageLength out of range")) }

        vm.uiState.test {
            advanceUntilIdle()
            vm.setMaxMessageLength(10_000)
            assertThat(vm.uiState.value.settings?.maxMessageLength).isEqualTo(10_000)
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertThat(repo.updates).hasSize(1)
            assertThat(state.settings?.maxMessageLength).isEqualTo(666)
            assertThat(state.errorMessage).isEqualTo("maxMessageLength out of range")
        }
    }

    @Test
    fun `re-entering the current character limit does not call the API`() = runTest(dispatcher) {
        val vm = loadedViewModel(UserSettings(maxMessageLength = 666))
        advanceUntilIdle()

        vm.setMaxMessageLength(666)
        advanceUntilIdle()

        assertThat(repo.updates).isEmpty()
    }

    @Test
    fun `entering the server default when the account has no stored limit does not call the API`() =
        runTest(dispatcher) {
            // The row shows 666 for a null stored value, so "setting" 666 changes nothing.
            val vm = loadedViewModel(UserSettings(maxMessageLength = null))
            advanceUntilIdle()

            vm.setMaxMessageLength(SettingsBounds.DEFAULT_MAX_MESSAGE_LENGTH)
            advanceUntilIdle()

            assertThat(repo.updates).isEmpty()
        }

    // --- messagesPerPage -----------------------------------------------------

    @Test
    fun `setting messages per page saves only that field`() = runTest(dispatcher) {
        val vm = loadedViewModel(UserSettings(messagesPerPage = 20))

        vm.uiState.test {
            advanceUntilIdle()
            vm.setMessagesPerPage(30)

            assertThat(vm.uiState.value.settings?.messagesPerPage).isEqualTo(30)
            advanceUntilIdle()

            val sent = repo.updates.single()
            assertThat(sent.messagesPerPage).isEqualTo(30)
            assertThat(sent.touchedFieldNames()).containsExactly("messagesPerPage")
            assertThat(expectMostRecentItem().settings?.messagesPerPage).isEqualTo(30)
        }
    }

    @Test
    fun `messages per page outside the documented ten to thirty is refused without a request`() =
        runTest(dispatcher) {
            val vm = loadedViewModel(UserSettings(messagesPerPage = 20))

            vm.uiState.test {
                advanceUntilIdle()
                vm.setMessagesPerPage(31)
                vm.setMessagesPerPage(9)
                advanceUntilIdle()

                assertThat(repo.updates).isEmpty()
                val state = expectMostRecentItem()
                assertThat(state.settings?.messagesPerPage).isEqualTo(20)
                assertThat(state.errorMessage).isEqualTo("Messages per page must be between 10 and 30.")
            }
        }

    @Test
    fun `a failed messages per page save rolls back and surfaces the error`() = runTest(dispatcher) {
        val vm = loadedViewModel(UserSettings(messagesPerPage = 20))
        rejectSaves()

        vm.uiState.test {
            advanceUntilIdle()
            vm.setMessagesPerPage(10)
            assertThat(vm.uiState.value.settings?.messagesPerPage).isEqualTo(10)
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertThat(state.settings?.messagesPerPage).isEqualTo(20)
            assertThat(state.errorMessage).isEqualTo(serverErrorMessage)
        }
    }

    @Test
    fun `the documented bounds match the help centre`() {
        assertThat(SettingsBounds.MESSAGES_PER_PAGE).isEqualTo(10..30)
        assertThat(SettingsBounds.DEFAULT_MAX_MESSAGE_LENGTH).isEqualTo(666)
        assertThat(SettingsBounds.DEFAULT_MESSAGES_PER_PAGE).isEqualTo(20)
    }
}
