package com.interlinedlist.android.core.appsettings.device

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.appsettings.InMemorySharedPreferences
import com.interlinedlist.android.core.appsettings.domain.AppSettingsSeed
import com.interlinedlist.android.core.appsettings.domain.AppSettingsSeedSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Test

/**
 * The device identity this install reports. A second store over the same preferences
 * stands in for a process restart, which is the property that matters: an id that
 * changed per launch would litter the account with dead devices.
 */
class SharedPrefsAppDeviceStoreTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val prefs = InMemorySharedPreferences()

    private fun store() = SharedPrefsAppDeviceStore(prefs, json)

    @Test
    fun `the device id is generated once and survives a restart`() {
        val first = store().deviceId()

        // Same instance, and a brand-new instance over the same file (a cold start).
        assertThat(store().deviceId()).isEqualTo(first)
        assertThat(SharedPrefsAppDeviceStore(prefs, json).deviceId()).isEqualTo(first)
    }

    @Test
    fun `the device id matches the format the server validates`() {
        val id = store().deviceId()

        // ^[A-Za-z0-9][A-Za-z0-9._:-]{7,127}$ — see /help/api/app-settings.
        assertThat(id).matches("[A-Za-z0-9][A-Za-z0-9._:-]{7,127}")
        assertThat(id).startsWith("android-")
    }

    @Test
    fun `two installs get different ids`() {
        // Random per install, so it cannot correlate users or devices across accounts.
        val other = SharedPrefsAppDeviceStore(InMemorySharedPreferences(), json).deviceId()

        assertThat(store().deviceId()).isNotEqualTo(other)
    }

    @Test
    fun `an empty stored id is regenerated rather than sent`() {
        prefs.edit().putString("device_id", "").apply()

        assertThat(store().deviceId()).startsWith("android-")
    }

    @Test
    fun `the adopted seed round-trips across a restart`() {
        val seed = AppSettingsSeed(
            source = AppSettingsSeedSource.DEFAULT_DEVICE,
            schemaVersion = 3,
            settings = buildJsonObject {
                put("themeMode", "DARK")
                put("addAnotherAfterSaving", true)
            },
            sourceDeviceName = "Studio Pixel",
        )

        store().pendingSeed = seed

        val restored = SharedPrefsAppDeviceStore(prefs, json).pendingSeed
        assertThat(restored?.source).isEqualTo(AppSettingsSeedSource.DEFAULT_DEVICE)
        assertThat(restored?.schemaVersion).isEqualTo(3)
        assertThat(restored?.sourceDeviceName).isEqualTo("Studio Pixel")
        assertThat(restored?.settings?.get("themeMode")?.jsonPrimitive?.content).isEqualTo("DARK")
    }

    @Test
    fun `clearing the seed leaves nothing behind for the next account`() {
        val subject = store()
        subject.pendingSeed = AppSettingsSeed(
            source = AppSettingsSeedSource.ACCOUNT,
            schemaVersion = 1,
            settings = buildJsonObject { put("a", 1) },
        )

        subject.pendingSeed = null

        assertThat(SharedPrefsAppDeviceStore(prefs, json).pendingSeed).isNull()
    }

    @Test
    fun `signing out forgets the account state but keeps the device id`() {
        val subject = store()
        val id = subject.deviceId()
        subject.hasBootstrapped = true
        subject.pendingSeed = AppSettingsSeed(
            source = AppSettingsSeedSource.ACCOUNT,
            schemaVersion = 1,
            settings = buildJsonObject { put("a", 1) },
        )

        subject.clearAccountState()

        // The id identifies the phone, not the account: signing in as someone else
        // must not strand a second device on the previous account.
        assertThat(subject.deviceId()).isEqualTo(id)
        // …but the next account gets its own first-run seeding.
        assertThat(subject.hasBootstrapped).isFalse()
        assertThat(subject.pendingSeed).isNull()
    }
}
