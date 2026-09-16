package com.interlinedlist.android.core.appsettings.device

import android.content.SharedPreferences
import com.interlinedlist.android.core.appsettings.di.AppDevicePreferences
import com.interlinedlist.android.core.appsettings.domain.AppSettingsSeed
import com.interlinedlist.android.core.appsettings.domain.AppSettingsSeedSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * This install's identity in the companion-app device registry, plus the one-shot
 * first-run state that goes with it.
 *
 * Behind an interface so the registration lifecycle can be unit-tested against an
 * in-memory double, following `ThemeSettingsStore` / `LastSeenNotificationStore`.
 */
interface AppDeviceStore {

    /**
     * The stable id this install reports as `deviceId`. Created on first access and
     * kept for the lifetime of the installation — see [SharedPrefsAppDeviceStore] for
     * what it is and, importantly, what it is not.
     */
    fun deviceId(): String

    /**
     * True once the first-run bootstrap has resolved — either it adopted a seed or
     * the server said there was nothing to seed from. Guards against re-seeding a
     * device that has since made its own choices.
     */
    var hasBootstrapped: Boolean

    /**
     * The settings this install adopted at bootstrap, waiting to be applied.
     *
     * **This is the seam for #78.** The sync work reads it once, applies it to the
     * device's preferences, and sets it back to null. #77 deliberately stops here: it
     * fetches and persists the document without interpreting a single key of it.
     */
    var pendingSeed: AppSettingsSeed?

    /**
     * Forgets the state that belonged to the account being signed out of ([pendingSeed]
     * and [hasBootstrapped]) while **keeping** [deviceId] — the id identifies the
     * phone, not the account, and the registry is already scoped per user. Signing in
     * as someone else therefore bootstraps again, from *their* main workstation.
     */
    fun clearAccountState()
}

/**
 * SharedPreferences-backed [AppDeviceStore].
 *
 * ## What the device id is
 *
 * A random v4 UUID with an `android-` prefix (e.g.
 * `android-3f6c…`), generated once on first use and persisted in plain (unencrypted)
 * preferences. It satisfies the server's `deviceId` format
 * (`^[A-Za-z0-9][A-Za-z0-9._:-]{7,127}$`) and is written with `commit()` rather than
 * `apply()` so a process death immediately after generation cannot lose it and strand
 * an orphan registration on the account.
 *
 * ## What it deliberately is not
 *
 * Not `ANDROID_ID`, not the build serial, not an advertising id, not the IMEI. Google
 * Play's user-data policy restricts persistent hardware identifiers, `Build.getSerial()`
 * needs `READ_PRIVILEGED_PHONE_STATE` (unavailable to normal apps), and Android's own
 * guidance is to use an app-scoped, self-generated id for exactly this purpose. A
 * random UUID also cannot correlate this user across apps or accounts, and disappears
 * when the app is uninstalled — which is the right lifetime: a reinstall is a new
 * machine as far as "which settings should this device start from" is concerned.
 *
 * It lives outside the encrypted session store on purpose: `SessionStore.clear()` wipes
 * that file on every sign-out, and an id that changed on each sign-out would leave a
 * trail of dead devices under Settings → Applications.
 */
@Singleton
class SharedPrefsAppDeviceStore @Inject constructor(
    @AppDevicePreferences private val prefs: SharedPreferences,
    private val json: Json,
) : AppDeviceStore {

    override fun deviceId(): String = synchronized(this) {
        prefs.getString(KEY_DEVICE_ID, null)?.takeIf { it.isNotBlank() }
            ?: newDeviceId().also { prefs.edit().putString(KEY_DEVICE_ID, it).commit() }
    }

    override var hasBootstrapped: Boolean
        get() = prefs.getBoolean(KEY_BOOTSTRAPPED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_BOOTSTRAPPED, value).apply()
        }

    override var pendingSeed: AppSettingsSeed?
        get() = readSeed()
        set(value) {
            if (value == null) {
                prefs.edit().remove(KEY_SEED_SOURCE).remove(KEY_SEED_SETTINGS)
                    .remove(KEY_SEED_SCHEMA).remove(KEY_SEED_SOURCE_NAME).apply()
            } else {
                prefs.edit()
                    .putString(KEY_SEED_SOURCE, value.source.wire)
                    .putString(KEY_SEED_SETTINGS, value.settings.toString())
                    .putInt(KEY_SEED_SCHEMA, value.schemaVersion)
                    .putString(KEY_SEED_SOURCE_NAME, value.sourceDeviceName)
                    .apply()
            }
        }

    override fun clearAccountState() {
        pendingSeed = null
        hasBootstrapped = false
    }

    private fun readSeed(): AppSettingsSeed? {
        val source = AppSettingsSeedSource.fromWire(prefs.getString(KEY_SEED_SOURCE, null))
            ?: return null
        val settings = prefs.getString(KEY_SEED_SETTINGS, null)
            ?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }
            ?: JsonObject(emptyMap())
        return AppSettingsSeed(
            source = source,
            schemaVersion = prefs.getInt(KEY_SEED_SCHEMA, 1),
            settings = settings,
            sourceDeviceName = prefs.getString(KEY_SEED_SOURCE_NAME, null),
        )
    }

    companion object {
        /** Plain preferences: the id is not a secret, and must outlive a sign-out. */
        const val PREFS_FILE = "il_app_device.prefs"

        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_BOOTSTRAPPED = "bootstrapped"
        private const val KEY_SEED_SOURCE = "seed_source"
        private const val KEY_SEED_SETTINGS = "seed_settings"
        private const val KEY_SEED_SCHEMA = "seed_schema_version"
        private const val KEY_SEED_SOURCE_NAME = "seed_source_device_name"

        /** Prefix kept so a device id is recognisable in a server-side device list. */
        private const val DEVICE_ID_PREFIX = "android-"

        /** `android-` + a random v4 UUID: 44 chars, well inside the server's 8..128. */
        fun newDeviceId(): String = DEVICE_ID_PREFIX + UUID.randomUUID()
    }
}
