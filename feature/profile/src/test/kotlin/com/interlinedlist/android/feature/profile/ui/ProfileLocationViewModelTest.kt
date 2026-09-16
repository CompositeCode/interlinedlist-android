package com.interlinedlist.android.feature.profile.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.profile.data.location.DeviceLocationResult
import com.interlinedlist.android.feature.profile.domain.Coordinates
import com.interlinedlist.android.feature.profile.domain.LocationUpdate
import com.interlinedlist.android.feature.profile.domain.UserSettings
import com.interlinedlist.android.feature.profile.ui.settings.ProfileLocationViewModel
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
 * The profile-location section (issue #37): the optional `latitude`/`longitude` pair
 * on the account, set from the device or by hand.
 *
 * Two properties matter more here than in the sibling settings, and both are pinned
 * down below: **the app never reads a position it was not explicitly asked to**, and
 * **refusing the permission costs the user nothing** — manual entry, clearing and the
 * rest of Settings all keep working.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileLocationViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeSettingsRepository
    private lateinit var device: FakeDeviceLocationSource

    private val seattle = Coordinates(47.6062, -122.3321)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeSettingsRepository()
        device = FakeDeviceLocationSource()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** A view model following a repository that already holds [settings]. */
    private suspend fun loadedViewModel(settings: UserSettings): ProfileLocationViewModel {
        repo.refreshResult = ApiResult.Success(settings)
        repo.refresh()
        return ProfileLocationViewModel(repo, device)
    }

    // --- Manual entry ---------------------------------------------------------

    @Test
    fun `saving typed coordinates PATCHes latitude and longitude alone`() = runTest(dispatcher) {
        val vm = loadedViewModel(UserSettings())

        vm.uiState.test {
            advanceUntilIdle()
            vm.saveLocation(47.6062, -122.3321)
            advanceUntilIdle()

            val sent = repo.updates.single()
            assertThat(sent.location).isEqualTo(LocationUpdate.Set(seattle))
            assertThat(sent.touchedFieldNames()).containsExactly("latitude", "longitude")
            val state = expectMostRecentItem()
            assertThat(state.coordinates).isEqualTo(seattle)
            assertThat(state.isSaving).isFalse()
            assertThat(state.notice).isNull()
        }
    }

    @Test
    fun `a typed coordinate is saved exactly as entered`() = runTest(dispatcher) {
        // Rounding belongs to device readings only: what the user typed is their choice.
        val vm = loadedViewModel(UserSettings())
        advanceUntilIdle()

        vm.saveLocation(47.60621, -122.33211)
        advanceUntilIdle()

        assertThat(repo.updates.single().location)
            .isEqualTo(LocationUpdate.Set(Coordinates(47.60621, -122.33211)))
    }

    @Test
    fun `an out-of-range latitude is refused without a request`() = runTest(dispatcher) {
        val vm = loadedViewModel(UserSettings())
        advanceUntilIdle()

        vm.saveLocation(91.0, 0.0)
        advanceUntilIdle()

        assertThat(repo.updates).isEmpty()
        assertThat(vm.uiState.value.notice?.text)
            .isEqualTo(ProfileLocationViewModel.LATITUDE_RANGE_NOTE)
        assertThat(vm.uiState.value.notice?.isError).isTrue()
        assertThat(vm.uiState.value.coordinates).isNull()
    }

    @Test
    fun `an out-of-range longitude is refused without a request`() = runTest(dispatcher) {
        val vm = loadedViewModel(UserSettings())
        advanceUntilIdle()

        vm.saveLocation(0.0, -180.5)
        advanceUntilIdle()

        assertThat(repo.updates).isEmpty()
        assertThat(vm.uiState.value.notice?.text)
            .isEqualTo(ProfileLocationViewModel.LONGITUDE_RANGE_NOTE)
    }

    @Test
    fun `both bounds are reported when both entries are impossible`() = runTest(dispatcher) {
        val vm = loadedViewModel(UserSettings())
        advanceUntilIdle()

        vm.saveLocation(-90.001, 200.0)
        advanceUntilIdle()

        assertThat(repo.updates).isEmpty()
        assertThat(vm.uiState.value.notice?.text)
            .contains(ProfileLocationViewModel.LATITUDE_RANGE_NOTE)
        assertThat(vm.uiState.value.notice?.text)
            .contains(ProfileLocationViewModel.LONGITUDE_RANGE_NOTE)
    }

    @Test
    fun `the exact bounds are accepted`() = runTest(dispatcher) {
        val vm = loadedViewModel(UserSettings())
        advanceUntilIdle()

        vm.saveLocation(-90.0, 180.0)
        advanceUntilIdle()

        assertThat(repo.updates.single().location)
            .isEqualTo(LocationUpdate.Set(Coordinates(-90.0, 180.0)))
    }

    @Test
    fun `a value that is not a coordinate at all is refused without a request`() =
        runTest(dispatcher) {
            val vm = loadedViewModel(UserSettings())
            advanceUntilIdle()

            vm.saveLocation(Double.NaN, Double.POSITIVE_INFINITY)
            advanceUntilIdle()

            assertThat(repo.updates).isEmpty()
        }

    @Test
    fun `saving the coordinates already stored does not call the API`() = runTest(dispatcher) {
        val vm = loadedViewModel(UserSettings(latitude = 47.6062, longitude = -122.3321))
        advanceUntilIdle()

        vm.saveLocation(47.6062, -122.3321)
        advanceUntilIdle()

        assertThat(repo.updates).isEmpty()
        assertThat(vm.uiState.value.coordinates).isEqualTo(seattle)
    }

    @Test
    fun `a rejected save leaves the stored location showing and reports why`() =
        runTest(dispatcher) {
            val vm = loadedViewModel(UserSettings(latitude = 47.6062, longitude = -122.3321))
            repo.updateResult = { ApiResult.Failure(AppError.Server("boom")) }
            advanceUntilIdle()

            vm.saveLocation(10.0, 10.0)
            advanceUntilIdle()

            // Never claimed, so nothing to roll back: the row still shows server truth.
            assertThat(vm.uiState.value.coordinates).isEqualTo(seattle)
            assertThat(vm.uiState.value.isSaving).isFalse()
            assertThat(vm.uiState.value.notice?.text)
                .isEqualTo("InterlinedList is having trouble right now. Try again shortly.")
            assertThat(vm.uiState.value.notice?.isError).isTrue()
        }

    // --- Reading the device ---------------------------------------------------

    @Test
    fun `nothing reads the device until the user asks`() = runTest(dispatcher) {
        device.result = DeviceLocationResult.Available(Coordinates(47.61, -122.33))
        device.granted = true
        loadedViewModel(UserSettings())
        advanceUntilIdle()

        // Constructing and loading the section must not take a position.
        assertThat(device.readCount).isEqualTo(0)
        assertThat(repo.updates).isEmpty()
    }

    @Test
    fun `a device reading is rounded to about a kilometre and saved`() = runTest(dispatcher) {
        device.granted = true
        device.result = DeviceLocationResult.Available(Coordinates(47.60621, -122.33207))
        val vm = loadedViewModel(UserSettings())
        advanceUntilIdle()

        vm.useDeviceLocation()
        advanceUntilIdle()

        assertThat(device.readCount).isEqualTo(1)
        val sent = repo.updates.single()
        assertThat(sent.location).isEqualTo(LocationUpdate.Set(Coordinates(47.61, -122.33)))
        assertThat(sent.touchedFieldNames()).containsExactly("latitude", "longitude")
        assertThat(vm.uiState.value.coordinates).isEqualTo(Coordinates(47.61, -122.33))
        assertThat(vm.uiState.value.isReadingDevice).isFalse()
    }

    @Test
    fun `a reading without the permission saves nothing and keeps manual entry available`() =
        runTest(dispatcher) {
            // Defence in depth: even if the UI called this without a grant, the source
            // refuses and the section says so rather than capturing anything.
            device.result = DeviceLocationResult.PermissionMissing
            val vm = loadedViewModel(UserSettings())
            advanceUntilIdle()

            vm.useDeviceLocation()
            advanceUntilIdle()

            assertThat(repo.updates).isEmpty()
            assertThat(vm.uiState.value.coordinates).isNull()
            assertThat(vm.uiState.value.notice?.text)
                .isEqualTo(ProfileLocationViewModel.PERMISSION_DENIED_NOTE)
            assertThat(vm.uiState.value.notice?.isError).isFalse()

            // ...and typing it in still works.
            vm.saveLocation(47.6062, -122.3321)
            advanceUntilIdle()
            assertThat(repo.updates.single().location).isEqualTo(LocationUpdate.Set(seattle))
            assertThat(vm.uiState.value.coordinates).isEqualTo(seattle)
        }

    @Test
    fun `a device with no fix reports it and changes nothing`() = runTest(dispatcher) {
        device.granted = true
        device.result = DeviceLocationResult.Unavailable
        val vm = loadedViewModel(UserSettings(latitude = 47.6062, longitude = -122.3321))
        advanceUntilIdle()

        vm.useDeviceLocation()
        advanceUntilIdle()

        assertThat(repo.updates).isEmpty()
        assertThat(vm.uiState.value.coordinates).isEqualTo(seattle)
        assertThat(vm.uiState.value.notice?.text).isEqualTo(ProfileLocationViewModel.NO_FIX_NOTE)
    }

    // --- Refusing the permission ---------------------------------------------

    @Test
    fun `refusing the permission leaves everything else working`() = runTest(dispatcher) {
        val vm = loadedViewModel(UserSettings(latitude = 47.6062, longitude = -122.3321))
        advanceUntilIdle()

        vm.onPermissionDenied(permanently = false)
        advanceUntilIdle()

        // Nothing was read, nothing was sent, nothing was lost.
        assertThat(device.readCount).isEqualTo(0)
        assertThat(repo.updates).isEmpty()
        assertThat(vm.uiState.value.coordinates).isEqualTo(seattle)
        assertThat(vm.uiState.value.isReadingDevice).isFalse()
        val notice = vm.uiState.value.notice
        assertThat(notice?.text).isEqualTo(ProfileLocationViewModel.PERMISSION_DENIED_NOTE)
        // A refusal is a choice, not an error.
        assertThat(notice?.isError).isFalse()
        assertThat(notice?.text).contains("enter coordinates below")

        // Manual entry still works afterwards, including over a stored location.
        vm.saveLocation(10.5, 20.5)
        advanceUntilIdle()
        assertThat(vm.uiState.value.coordinates).isEqualTo(Coordinates(10.5, 20.5))
    }

    @Test
    fun `a permanent refusal says where the decision can be changed`() = runTest(dispatcher) {
        val vm = loadedViewModel(UserSettings())
        advanceUntilIdle()

        vm.onPermissionDenied(permanently = true)
        advanceUntilIdle()

        assertThat(repo.updates).isEmpty()
        val notice = vm.uiState.value.notice
        assertThat(notice?.text).isEqualTo(ProfileLocationViewModel.PERMISSION_BLOCKED_NOTE)
        assertThat(notice?.isError).isFalse()
        assertThat(notice?.text).contains("Android Settings")
        assertThat(notice?.text).contains("enter coordinates below")

        // The section is still fully usable.
        vm.saveLocation(47.6062, -122.3321)
        advanceUntilIdle()
        assertThat(vm.uiState.value.coordinates).isEqualTo(seattle)
    }

    @Test
    fun `a notice can be dismissed`() = runTest(dispatcher) {
        val vm = loadedViewModel(UserSettings())
        advanceUntilIdle()
        vm.onPermissionDenied(permanently = false)

        vm.dismissNotice()

        assertThat(vm.uiState.value.notice).isNull()
    }

    // --- Removal: not offered, because the API cannot do it ------------------

    @Test
    fun `a stored location is replaced rather than removed`() = runTest(dispatcher) {
        // The only supported way to change a saved location: PATCH a different pair.
        // `PATCH /api/user/update` refuses every spelling of "no value" with
        // 400 {"error":"latitude must be a number between -90 and 90"}, so nothing
        // here can ask for a removal — see LocationUpdate.Clear.
        val vm = loadedViewModel(UserSettings(latitude = 47.6062, longitude = -122.3321))
        advanceUntilIdle()

        vm.saveLocation(45.52, -122.68)
        advanceUntilIdle()

        val sent = repo.updates.single()
        assertThat(sent.location).isEqualTo(LocationUpdate.Set(Coordinates(45.52, -122.68)))
        assertThat(sent.touchedFieldNames()).containsExactly("latitude", "longitude")
        assertThat(vm.uiState.value.coordinates).isEqualTo(Coordinates(45.52, -122.68))
    }

    @Test
    fun `no user action can send a clear`() = runTest(dispatcher) {
        // The guarantee is structural — the view model builds LocationUpdate.Set and
        // nothing else — and this pins it against every entry point at once, so a
        // future edit cannot quietly reintroduce a request the server always rejects.
        device.granted = true
        device.result = DeviceLocationResult.Available(Coordinates(47.61, -122.33))
        val vm = loadedViewModel(UserSettings(latitude = 47.6062, longitude = -122.3321))
        advanceUntilIdle()

        vm.saveLocation(10.0, 20.0)
        vm.useDeviceLocation()
        vm.onPermissionDenied(permanently = true)
        vm.dismissNotice()
        advanceUntilIdle()

        assertThat(repo.updates).isNotEmpty()
        assertThat(repo.updates.map { it.location })
            .doesNotContain(LocationUpdate.Clear)
    }

    // --- Following the shared settings cache ----------------------------------

    @Test
    fun `the section shows the stored location after a refresh`() = runTest(dispatcher) {
        val vm = loadedViewModel(UserSettings())

        vm.uiState.test {
            advanceUntilIdle()
            assertThat(vm.uiState.value.coordinates).isNull()

            // A refresh anywhere in the app (the Settings screen owns the loading).
            repo.refreshResult =
                ApiResult.Success(UserSettings(latitude = 47.6062, longitude = -122.3321))
            repo.refresh()
            advanceUntilIdle()

            assertThat(expectMostRecentItem().coordinates).isEqualTo(seattle)
        }
    }

    @Test
    fun `half a coordinate reads as no location at all`() = runTest(dispatcher) {
        val vm = loadedViewModel(UserSettings(latitude = 47.6062, longitude = null))
        advanceUntilIdle()

        assertThat(vm.uiState.value.coordinates).isNull()
    }
}
