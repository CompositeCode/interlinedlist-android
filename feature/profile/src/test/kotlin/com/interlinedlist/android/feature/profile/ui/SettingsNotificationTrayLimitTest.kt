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
 * Issue #35: the notification tray limit, saved from Settings.
 *
 * The help centre publishes the range outright — `/help/settings`: "Notification tray
 * limit: How many notifications the bell tray holds before older ones drop off. The
 * default is 20 and you can set any value from 10 to 40" — and `/help/api/notifications`
 * corroborates it ("default 20, clamped to 10-40"), so out-of-range input is refused
 * here rather than spent on a request the server would reject.
 *
 * Like every other preference it PATCHes alone, applies optimistically and rolls back
 * when the save fails — which matters more here than for a cosmetic toggle, because
 * `:feature:notifications` sizes both its list and its system-tray group by the value.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsNotificationTrayLimitTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeSettingsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeSettingsRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun loadedViewModel(settings: UserSettings): SettingsViewModel {
        repo.refreshResult = ApiResult.Success(settings)
        return SettingsViewModel(repo)
    }

    private val serverErrorMessage = "InterlinedList is having trouble right now. Try again shortly."

    private val outOfRangeMessage =
        "Notification tray limit must be between " +
            "${SettingsBounds.NOTIFICATION_TRAY_LIMIT.first} and " +
            "${SettingsBounds.NOTIFICATION_TRAY_LIMIT.last}."

    @Test
    fun `setting the tray limit PATCHes only that field`() = runTest(dispatcher) {
        val vm = loadedViewModel(UserSettings(notificationTrayLimit = 20))

        vm.uiState.test {
            advanceUntilIdle()
            vm.setNotificationTrayLimit(40)

            // Applied optimistically, before the request comes back.
            assertThat(vm.uiState.value.settings?.notificationTrayLimit).isEqualTo(40)
            assertThat(vm.uiState.value.isSaving).isTrue()
            advanceUntilIdle()

            val sent = repo.updates.single()
            assertThat(sent.notificationTrayLimit).isEqualTo(40)
            assertThat(sent.touchedFieldNames()).containsExactly("notificationTrayLimit")
            val state = expectMostRecentItem()
            assertThat(state.settings?.notificationTrayLimit).isEqualTo(40)
            assertThat(state.isSaving).isFalse()
            assertThat(state.errorMessage).isNull()
        }
    }

    @Test
    fun `a limit above the documented maximum is refused without a request`() = runTest(dispatcher) {
        val vm = loadedViewModel(UserSettings(notificationTrayLimit = 20))

        vm.uiState.test {
            advanceUntilIdle()
            vm.setNotificationTrayLimit(SettingsBounds.NOTIFICATION_TRAY_LIMIT.last + 1)
            advanceUntilIdle()

            assertThat(repo.updates).isEmpty()
            val state = expectMostRecentItem()
            assertThat(state.settings?.notificationTrayLimit).isEqualTo(20)
            assertThat(state.isSaving).isFalse()
            assertThat(state.errorMessage).isEqualTo(outOfRangeMessage)
        }
    }

    @Test
    fun `a limit below the documented minimum is refused without a request`() = runTest(dispatcher) {
        val vm = loadedViewModel(UserSettings(notificationTrayLimit = 20))

        vm.uiState.test {
            advanceUntilIdle()
            vm.setNotificationTrayLimit(SettingsBounds.NOTIFICATION_TRAY_LIMIT.first - 1)
            advanceUntilIdle()

            assertThat(repo.updates).isEmpty()
            val state = expectMostRecentItem()
            assertThat(state.settings?.notificationTrayLimit).isEqualTo(20)
            assertThat(state.errorMessage).isEqualTo(outOfRangeMessage)
        }
    }

    @Test
    fun `both ends of the documented range are accepted`() = runTest(dispatcher) {
        val vm = loadedViewModel(UserSettings(notificationTrayLimit = 20))
        advanceUntilIdle()

        vm.setNotificationTrayLimit(SettingsBounds.NOTIFICATION_TRAY_LIMIT.first)
        advanceUntilIdle()
        vm.setNotificationTrayLimit(SettingsBounds.NOTIFICATION_TRAY_LIMIT.last)
        advanceUntilIdle()

        assertThat(repo.updates.map { it.notificationTrayLimit }).containsExactly(10, 40).inOrder()
        assertThat(vm.uiState.value.errorMessage).isNull()
    }

    @Test
    fun `a failed save rolls back to the stored limit and surfaces the error`() = runTest(dispatcher) {
        val vm = loadedViewModel(UserSettings(notificationTrayLimit = 20))
        repo.updateResult = { ApiResult.Failure(AppError.Server("boom")) }

        vm.uiState.test {
            advanceUntilIdle()
            vm.setNotificationTrayLimit(40)
            assertThat(vm.uiState.value.settings?.notificationTrayLimit).isEqualTo(40)
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertThat(state.settings?.notificationTrayLimit).isEqualTo(20)
            assertThat(state.isSaving).isFalse()
            assertThat(state.errorMessage).isEqualTo(serverErrorMessage)
        }
    }

    @Test
    fun `re-entering the current limit does not call the API`() = runTest(dispatcher) {
        val vm = loadedViewModel(UserSettings(notificationTrayLimit = 30))
        advanceUntilIdle()

        vm.setNotificationTrayLimit(30)
        advanceUntilIdle()

        assertThat(repo.updates).isEmpty()
    }

    @Test
    fun `entering the server default when the account has no stored limit does not call the API`() =
        runTest(dispatcher) {
            // The row shows 20 for a null stored value, so "setting" 20 changes nothing.
            val vm = loadedViewModel(UserSettings(notificationTrayLimit = null))
            advanceUntilIdle()

            vm.setNotificationTrayLimit(SettingsBounds.DEFAULT_NOTIFICATION_TRAY_LIMIT)
            advanceUntilIdle()

            assertThat(repo.updates).isEmpty()
        }

    @Test
    fun `the documented bounds match the help centre`() {
        assertThat(SettingsBounds.NOTIFICATION_TRAY_LIMIT).isEqualTo(10..40)
        assertThat(SettingsBounds.DEFAULT_NOTIFICATION_TRAY_LIMIT).isEqualTo(20)
    }
}
