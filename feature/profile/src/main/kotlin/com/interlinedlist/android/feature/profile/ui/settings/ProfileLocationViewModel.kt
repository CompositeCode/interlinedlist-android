package com.interlinedlist.android.feature.profile.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.profile.data.SettingsRepository
import com.interlinedlist.android.feature.profile.data.location.DeviceLocationResult
import com.interlinedlist.android.feature.profile.data.location.DeviceLocationSource
import com.interlinedlist.android.feature.profile.domain.Coordinates
import com.interlinedlist.android.feature.profile.domain.CoordinateBounds
import com.interlinedlist.android.feature.profile.domain.LocationUpdate
import com.interlinedlist.android.feature.profile.domain.UserSettingsUpdate
import com.interlinedlist.android.feature.profile.domain.coarsened
import com.interlinedlist.android.feature.profile.domain.coordinates
import com.interlinedlist.android.feature.profile.domain.isValid
import com.interlinedlist.android.feature.profile.ui.common.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A short line under the location controls explaining why nothing was saved, or what
 * to do next. [isError] separates "something went wrong" (shown in the error colour)
 * from "you said no, and that is fine" — refusing the permission is a choice the app
 * respects, not a failure to report in red.
 */
data class LocationNotice(val text: String, val isError: Boolean = false)

/**
 * State of the profile-location section.
 *
 * [coordinates] is always what the **server** last said: nothing is applied
 * optimistically here. The other settings rows can show a change before it is
 * confirmed and roll it back, because a toggle that briefly lies about a page size is
 * harmless; a row claiming the account stores a position it does not (or no longer
 * stores one it does) would be a privacy claim the app cannot back up.
 */
data class ProfileLocationUiState(
    val coordinates: Coordinates? = null,
    val isSaving: Boolean = false,
    val isReadingDevice: Boolean = false,
    val notice: LocationNotice? = null,
)

/**
 * Drives the "Profile location" settings group: the optional `latitude`/`longitude`
 * pair on the account, which the help centre describes as "Optional location for your
 * profile, used by the Weather widget and similar location-aware features".
 *
 * It is a view model of its own rather than more methods on [SettingsViewModel]
 * because it has a dependency none of the other preferences have — [DeviceLocationSource],
 * the single component that may read the device's position — and a different failure
 * vocabulary (permission refused, no fix). It shares the same [SettingsRepository],
 * whose cache is app-wide, so a save here reaches the Settings screen's own state and
 * a refresh there reaches this section. Loading is left to [SettingsViewModel]: this
 * section follows the shared cache and never issues a fetch of its own.
 *
 * Three rules hold throughout:
 * - **The position is read only from an explicit user action** ([useDeviceLocation]),
 *   never on load, never on a schedule.
 * - **Refusing is free.** Any denial path ends with the section still usable: the
 *   coordinates can be typed in, and nothing else in the app changes.
 * - **Only [LocationUpdate.Set] is ever sent.** The endpoint has no way to unset a
 *   saved location — every spelling of "no value" comes back
 *   `400 {"error":"latitude must be a number between -90 and 90"}` — so this class
 *   cannot express a clear at all, rather than offering one that always fails. See
 *   [LocationUpdate.Clear] for the probe results.
 */
@HiltViewModel
class ProfileLocationViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val deviceLocation: DeviceLocationSource,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileLocationUiState())
    val uiState: StateFlow<ProfileLocationUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeSettings().collect { settings ->
                _uiState.update { it.copy(coordinates = settings?.coordinates) }
            }
        }
    }

    /**
     * Saves coordinates the user typed in, exactly as typed.
     *
     * Values outside the meaning of latitude and longitude are refused here, before
     * any request: an entry of 91° north does not exist, so there is nothing to ask
     * the server about.
     *
     * This is also the only way to change a location already stored — the account
     * offers no way to remove one (see [LocationUpdate.Clear]) — so a save over an
     * existing pair replaces it.
     */
    fun saveLocation(latitude: Double, longitude: Double) {
        val entered = Coordinates(latitude, longitude)
        if (!entered.isValid) {
            val note = LocationNotice(outOfRangeNote(entered), isError = true)
            _uiState.update { it.copy(notice = note) }
            return
        }
        if (entered == _uiState.value.coordinates) {
            _uiState.update { it.copy(notice = null) }
            return
        }
        save(entered)
    }

    /**
     * Reads this device's position once and saves it, rounded to about a kilometre
     * (see [coarsened]).
     *
     * Call this only after `ACCESS_COARSE_LOCATION` has been granted; if it has not
     * been, the source answers [DeviceLocationResult.PermissionMissing] and nothing is
     * read, so a mis-wired caller cannot capture a position behind the user's back.
     */
    fun useDeviceLocation() {
        if (_uiState.value.isReadingDevice) return
        _uiState.update { it.copy(isReadingDevice = true, notice = null) }
        viewModelScope.launch {
            val result = deviceLocation.currentCoordinates()
            _uiState.update { it.copy(isReadingDevice = false) }
            when (result) {
                is DeviceLocationResult.Available -> {
                    val coarse = result.coordinates.coarsened()
                    if (coarse.isValid) save(coarse) else note(NO_FIX_NOTE)
                }
                DeviceLocationResult.PermissionMissing -> note(PERMISSION_DENIED_NOTE)
                DeviceLocationResult.Unavailable -> note(NO_FIX_NOTE)
            }
        }
    }

    /**
     * Records that the user refused the system permission dialog.
     *
     * Nothing is saved, nothing is retried and no other preference is touched — the
     * section simply says that coordinates can still be entered by hand. When Android
     * will no longer show the dialog ([permanently]), the note adds where to change
     * the decision, since the button alone can no longer do it.
     */
    fun onPermissionDenied(permanently: Boolean) {
        _uiState.update {
            it.copy(
                isReadingDevice = false,
                notice = LocationNotice(
                    if (permanently) PERMISSION_BLOCKED_NOTE else PERMISSION_DENIED_NOTE,
                ),
            )
        }
    }

    /** Dismisses the current notice. */
    fun dismissNotice() = _uiState.update { it.copy(notice = null) }

    /**
     * `PATCH /api/user/update` with the two location fields and nothing else.
     *
     * Takes [Coordinates] rather than a [LocationUpdate] on purpose: a clear is not
     * representable here, so the one request the API refuses cannot be built by
     * accident. What lands in the state afterwards is what the server echoed back.
     */
    private fun save(coordinates: Coordinates) {
        _uiState.update { it.copy(isSaving = true, notice = null) }
        viewModelScope.launch {
            val update = UserSettingsUpdate(location = LocationUpdate.Set(coordinates))
            when (val result = repository.update(update)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(coordinates = result.data.coordinates, isSaving = false, notice = null)
                }

                is ApiResult.Failure -> _uiState.update {
                    it.copy(
                        isSaving = false,
                        notice = LocationNotice(result.error.toUserMessage(), isError = true),
                    )
                }
            }
        }
    }

    private fun note(text: String) = _uiState.update { it.copy(notice = LocationNotice(text)) }

    /** Names whichever of the two entries is not a coordinate. */
    private fun outOfRangeNote(entered: Coordinates): String {
        val latitudeBad = entered.latitude !in CoordinateBounds.LATITUDE
        val longitudeBad = entered.longitude !in CoordinateBounds.LONGITUDE
        return when {
            latitudeBad && longitudeBad -> "$LATITUDE_RANGE_NOTE $LONGITUDE_RANGE_NOTE"
            latitudeBad -> LATITUDE_RANGE_NOTE
            else -> LONGITUDE_RANGE_NOTE
        }
    }

    companion object {
        /** Shown when the user declines the system dialog; the section stays usable. */
        const val PERMISSION_DENIED_NOTE: String =
            "Location permission wasn't granted, so nothing was read from this device. " +
                "You can still enter coordinates below, or leave your location unset."

        /** Shown when Android will not offer the dialog again. */
        const val PERMISSION_BLOCKED_NOTE: String =
            "Location permission is off for InterlinedList, so nothing was read from this " +
                "device. You can still enter coordinates below, or allow Location for this " +
                "app in Android Settings."

        /** Permission held, but the device had nothing to give. */
        const val NO_FIX_NOTE: String =
            "Couldn't get a location from this device. Check that Location is switched on, " +
                "or enter coordinates below."

        const val LATITUDE_RANGE_NOTE: String = "Latitude must be between -90 and 90."

        const val LONGITUDE_RANGE_NOTE: String = "Longitude must be between -180 and 180."
    }
}
