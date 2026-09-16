package com.interlinedlist.android.core.appsettings.domain

import kotlinx.serialization.json.JsonObject

/** Where the settings a fresh install starts from were resolved from. */
enum class AppSettingsSeedSource(val wire: String) {
    /** This device already had its own document (a reinstall over an existing device id). */
    SELF("self"),

    /** The account's "main workstation" — the web's documented first-run behaviour. */
    DEFAULT_DEVICE("default-device"),

    /** The account-scoped (shared) document. */
    ACCOUNT("account"),
    ;

    companion object {
        fun fromWire(wire: String?): AppSettingsSeedSource? =
            entries.firstOrNull { it.wire == wire }
    }
}

/**
 * What `GET /api/user/app-settings/{appKey}/bootstrap` resolved for this install.
 *
 * [settings] is the opaque, client-owned document the server round-trips byte for
 * byte; this issue (#77) deliberately does **not** interpret it. It is persisted as
 * [com.interlinedlist.android.core.appsettings.device.AppDeviceStore.pendingSeed] and
 * left for the settings-sync work (#78) to apply to the device's preferences and then
 * clear — that pending value is the seam between the two.
 *
 * @param sourceDeviceName the "main workstation" the settings came from, when
 *   [source] is [AppSettingsSeedSource.DEFAULT_DEVICE]; null otherwise. Worth keeping
 *   so #78 can tell the user *which* machine their new phone was set up from.
 */
data class AppSettingsSeed(
    val source: AppSettingsSeedSource,
    val schemaVersion: Int,
    val settings: JsonObject,
    val sourceDeviceName: String? = null,
)
