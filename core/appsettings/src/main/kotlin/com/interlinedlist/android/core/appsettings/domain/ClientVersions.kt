package com.interlinedlist.android.core.appsettings.domain

/**
 * The optional `appVersion` / `osVersion` a registration reports, so the web's
 * Applications list can show what this machine is running. Both are nullable: the
 * registry accepts a registration without them, and a missing value must never stop
 * the device being registered.
 */
data class ClientVersions(
    val appVersion: String?,
    val osVersion: String?,
)
