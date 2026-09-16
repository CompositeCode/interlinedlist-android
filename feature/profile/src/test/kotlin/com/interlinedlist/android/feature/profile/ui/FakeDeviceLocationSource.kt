package com.interlinedlist.android.feature.profile.ui

import com.interlinedlist.android.feature.profile.data.location.DeviceLocationResult
import com.interlinedlist.android.feature.profile.data.location.DeviceLocationSource

/**
 * In-memory [DeviceLocationSource] for view-model tests: [result] decides what a
 * reading answers, and [readCount] records how many times the device was consulted —
 * which is how a test proves the app never takes a position it was not asked to.
 */
class FakeDeviceLocationSource(
    var result: DeviceLocationResult = DeviceLocationResult.PermissionMissing,
    var granted: Boolean = false,
) : DeviceLocationSource {

    var readCount: Int = 0
        private set

    override fun hasCoarsePermission(): Boolean = granted

    override suspend fun currentCoordinates(): DeviceLocationResult {
        readCount++
        return result
    }
}
