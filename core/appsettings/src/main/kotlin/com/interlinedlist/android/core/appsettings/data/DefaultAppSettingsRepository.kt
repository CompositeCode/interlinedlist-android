package com.interlinedlist.android.core.appsettings.data

import com.interlinedlist.android.core.appsettings.data.remote.AppSettingsApi
import com.interlinedlist.android.core.appsettings.data.remote.dto.RegisterDeviceRequest
import com.interlinedlist.android.core.appsettings.data.remote.dto.toDomain
import com.interlinedlist.android.core.appsettings.domain.AppSettingsSeed
import com.interlinedlist.android.core.appsettings.domain.AppSettingsSeedSource
import com.interlinedlist.android.core.appsettings.domain.CompanionApp
import com.interlinedlist.android.core.appsettings.domain.RegisteredDevice
import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.core.common.result.map
import com.interlinedlist.android.core.network.error.safeApiCall
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject

/** `/api/user/app-settings/{appKey}/…` over the shared authed Retrofit stack. */
class DefaultAppSettingsRepository @Inject constructor(
    private val api: AppSettingsApi,
    private val json: Json,
    private val dispatchers: DispatcherProvider,
) : AppSettingsRepository {

    override suspend fun registerDevice(
        deviceId: String,
        deviceName: String,
        appVersion: String?,
        osVersion: String?,
    ): ApiResult<RegisteredDevice> = withContext(dispatchers.io) {
        safeApiCall(json) {
            api.registerDevice(
                appKey = CompanionApp.APP_KEY,
                body = RegisterDeviceRequest(
                    deviceId = deviceId,
                    deviceName = deviceName,
                    platform = CompanionApp.PLATFORM,
                    appVersion = appVersion,
                    osVersion = osVersion,
                    // Names this app in the web's Applications list the first time the
                    // account sees the key; ignored on every later registration.
                    appDisplayName = CompanionApp.APP_DISPLAY_NAME,
                ),
            )
        }.map { it.device.toDomain() }
    }

    override suspend fun deregisterDevice(deviceId: String): ApiResult<Unit> =
        withContext(dispatchers.io) {
            when (
                val result = safeApiCall(json) {
                    api.deregisterDevice(CompanionApp.APP_KEY, deviceId)
                }
            ) {
                is ApiResult.Success -> ApiResult.Success(Unit)
                // "No such device" is the state deregistration is trying to reach, so
                // a 404 is success — sign-out must be idempotent (the user may have
                // removed this device from the web already).
                is ApiResult.Failure ->
                    if (result.error is AppError.NotFound) ApiResult.Success(Unit) else result
            }
        }

    override suspend fun bootstrap(deviceId: String): ApiResult<AppSettingsSeed> =
        withContext(dispatchers.io) {
            safeApiCall(json) { api.bootstrap(CompanionApp.APP_KEY, deviceId) }.map { response ->
                AppSettingsSeed(
                    // An unrecognised provenance still carries usable settings; treat
                    // it as the shared account document rather than discarding it.
                    source = AppSettingsSeedSource.fromWire(response.source)
                        ?: AppSettingsSeedSource.ACCOUNT,
                    schemaVersion = response.schemaVersion,
                    settings = response.settings ?: JsonObject(emptyMap()),
                    sourceDeviceName = response.defaultDeviceName,
                )
            }
        }
}
