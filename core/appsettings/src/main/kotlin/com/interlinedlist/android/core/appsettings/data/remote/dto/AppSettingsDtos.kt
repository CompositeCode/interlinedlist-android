package com.interlinedlist.android.core.appsettings.data.remote.dto

import com.interlinedlist.android.core.appsettings.domain.CompanionApp
import com.interlinedlist.android.core.appsettings.domain.RegisteredDevice
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Body of `POST /api/user/app-settings/{appKey}/devices`.
 *
 * `deviceId` + `deviceName` + `platform` are required; `appVersion` / `osVersion` are
 * optional and are what the web shows next to the machine. Nulls are omitted by the
 * shared Json (`explicitNulls = false`).
 *
 * [platform] deliberately has **no default**: kotlinx.serialization omits a property
 * whose value equals its default, which would strip the required `platform` from the
 * body and earn a 400.
 */
@Serializable
data class RegisterDeviceRequest(
    val deviceId: String,
    val deviceName: String,
    val platform: String,
    val appVersion: String? = null,
    val osVersion: String? = null,
    val appDisplayName: String? = null,
)

/** `{ "device": { … } }` — the registration response (no `hasDeviceSettings` here). */
@Serializable
data class RegisterDeviceResponse(val device: RegisteredDeviceDto)

@Serializable
data class RegisteredDeviceDto(
    val deviceId: String,
    val deviceName: String? = null,
    val platform: String? = null,
    val isDefault: Boolean = false,
    val lastSeenAt: String? = null,
    val appVersion: String? = null,
    val osVersion: String? = null,
)

fun RegisteredDeviceDto.toDomain() = RegisteredDevice(
    deviceId = deviceId,
    deviceName = deviceName.orEmpty(),
    platform = platform ?: CompanionApp.PLATFORM,
    isDefault = isDefault,
    lastSeenAt = lastSeenAt,
    appVersion = appVersion,
    osVersion = osVersion,
)

/** `{ "deleted": true, "promotedDeviceId": … }` — the deregistration response. */
@Serializable
data class DeregisterDeviceResponse(
    val deleted: Boolean = false,
    val promotedDeviceId: String? = null,
)

/**
 * `GET /api/user/app-settings/{appKey}/bootstrap?deviceId=…`.
 *
 * The resolved document's fields are merged in at the **top level** next to `source`
 * (they are not nested under a `doc` key), and `scope`/`deviceId` describe the
 * *source* document rather than the requesting device. A 404 carries
 * `{ "source": "none" }`, which `safeApiCall` turns into `AppError.NotFound` — so
 * nothing but `source` is ever read from a failure.
 */
@Serializable
data class BootstrapResponse(
    val source: String? = null,
    val appKey: String? = null,
    val scope: String? = null,
    val deviceId: String? = null,
    val version: Int? = null,
    val updatedAt: String? = null,
    val schemaVersion: Int = 1,
    val settings: JsonObject? = null,
    val defaultDeviceId: String? = null,
    val defaultDeviceName: String? = null,
)
