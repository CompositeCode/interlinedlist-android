package com.interlinedlist.android.core.appsettings.data

import com.interlinedlist.android.core.appsettings.domain.AppSettingsSeed
import com.interlinedlist.android.core.appsettings.domain.RegisteredDevice
import com.interlinedlist.android.core.common.result.ApiResult

/**
 * The companion-app device registry as this app uses it. Stateless on purpose: *when*
 * to register, bootstrap or deregister is the lifecycle's job (see
 * [com.interlinedlist.android.core.appsettings.AppDeviceRegistrationManager]).
 */
interface AppSettingsRepository {

    /** Registers this device, or refreshes an existing registration. */
    suspend fun registerDevice(
        deviceId: String,
        deviceName: String,
        appVersion: String?,
        osVersion: String?,
    ): ApiResult<RegisteredDevice>

    /**
     * Retires this device's registration. An already-absent device (404) is reported
     * as success: teardown has to be idempotent.
     */
    suspend fun deregisterDevice(deviceId: String): ApiResult<Unit>

    /**
     * Resolves what a fresh install should seed from.
     * [com.interlinedlist.android.core.common.result.AppError.NotFound] means the
     * server answered `{ "source": "none" }` — there is nothing to seed from.
     */
    suspend fun bootstrap(deviceId: String): ApiResult<AppSettingsSeed>
}
