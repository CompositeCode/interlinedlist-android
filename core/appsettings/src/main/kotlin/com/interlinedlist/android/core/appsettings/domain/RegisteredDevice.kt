package com.interlinedlist.android.core.appsettings.domain

/**
 * A machine in the account's device registry for this app, as the server echoes it
 * back from a registration. The Applications screen that lists these is #79; this
 * type exists so registration can be asserted end to end (the device the server
 * stored is the device we asked it to store).
 */
data class RegisteredDevice(
    val deviceId: String,
    val deviceName: String,
    val platform: String,
    /** True when this device is the account's "main workstation" for this app. */
    val isDefault: Boolean,
    val lastSeenAt: String?,
    val appVersion: String?,
    val osVersion: String?,
)
