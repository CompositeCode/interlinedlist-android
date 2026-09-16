package com.interlinedlist.android.feature.profile.data.location

import com.interlinedlist.android.feature.profile.domain.Coordinates

/** The outcome of one attempt to read this device's position. */
sealed interface DeviceLocationResult {

    /** A fix was obtained. */
    data class Available(val coordinates: Coordinates) : DeviceLocationResult

    /**
     * `ACCESS_COARSE_LOCATION` is not held, so nothing was read. Setting a location
     * stays entirely optional, so this is an ordinary outcome and not an error.
     */
    data object PermissionMissing : DeviceLocationResult

    /**
     * The permission is held but the device produced no fix — location services off,
     * no usable provider, or nothing reported inside the time budget.
     */
    data object Unavailable : DeviceLocationResult
}

/**
 * Reads this device's approximate position, so Settings can offer "use my location"
 * as an alternative to typing coordinates in.
 *
 * Abstracted (DIP) for two reasons: the view model stays unit-testable with no Android
 * runtime, and there is exactly one place in the app that can touch the device's
 * position — [currentCoordinates] — which is called only from an explicit user action.
 * Nothing observes location; there is no background reader and no subscription.
 */
interface DeviceLocationSource {

    /** True when `ACCESS_COARSE_LOCATION` is currently granted to the app. */
    fun hasCoarsePermission(): Boolean

    /**
     * Takes a single reading. Answers [DeviceLocationResult.PermissionMissing] rather
     * than throwing when the permission is absent, so a caller can never capture a
     * position it was not granted.
     */
    suspend fun currentCoordinates(): DeviceLocationResult
}
