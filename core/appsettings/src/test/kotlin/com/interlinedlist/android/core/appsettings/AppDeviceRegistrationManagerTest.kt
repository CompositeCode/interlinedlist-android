package com.interlinedlist.android.core.appsettings

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.appsettings.domain.AppSettingsSeed
import com.interlinedlist.android.core.appsettings.domain.AppSettingsSeedSource
import com.interlinedlist.android.core.appsettings.domain.ClientVersions
import com.interlinedlist.android.core.appsettings.domain.RegisteredDevice
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.core.common.session.SessionTeardownTask
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Test

/**
 * The registration lifecycle: when Android appears under Settings → Applications, when
 * a brand-new install seeds itself from the account, and when the registration is
 * retired.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppDeviceRegistrationManagerTest {

    private val repository = FakeAppSettingsRepository()
    private val store = FakeAppDeviceStore(id = "android-this-phone")

    private fun manager(
        versions: ClientVersions = ClientVersions(appVersion = "0.1.0", osVersion = "14"),
    ) = AppDeviceRegistrationManager(
        repository = repository,
        store = store,
        deviceLabels = FakeDeviceLabelProvider(),
        clientVersions = versions,
    )

    private fun seed(source: AppSettingsSeedSource = AppSettingsSeedSource.DEFAULT_DEVICE) =
        AppSettingsSeed(
            source = source,
            schemaVersion = 1,
            settings = buildJsonObject { put("themeMode", "DARK") },
            sourceDeviceName = "Studio Pixel",
        )

    // ---- registration ------------------------------------------------------

    @Test
    fun `signing in registers this device under the shared device label`() = runTest {
        manager().registerForSession()

        assertThat(repository.registrations).hasSize(1)
        val registration = repository.registrations.single()
        assertThat(registration.deviceId).isEqualTo("android-this-phone")
        // The SAME label `sync-token` sends, so Sessions and Applications agree.
        assertThat(registration.deviceName).isEqualTo("InterlinedList Android · Pixel 8")
        assertThat(registration.appVersion).isEqualTo("0.1.0")
        assertThat(registration.osVersion).isEqualTo("14")
    }

    @Test
    fun `re-entering the signed-in shell does not re-register`() = runTest {
        val manager = manager()

        manager.registerForSession()
        manager.registerForSession()

        assertThat(repository.registrations).hasSize(1)
    }

    @Test
    fun `a device with no readable versions still registers`() = runTest {
        manager(ClientVersions(appVersion = null, osVersion = null)).registerForSession()

        assertThat(repository.registrations.single().appVersion).isNull()
        assertThat(repository.registrations).hasSize(1)
    }

    @Test
    fun `a failed registration never throws and never seeds`() = runTest {
        // Sign-in must not depend on this: the shell calls it, not the auth path, and
        // an offline phone simply stays unregistered until it is not.
        repository.registerResult = ApiResult.Failure(AppError.Network("offline"))
        repository.bootstrapResult = ApiResult.Success(seed())

        manager().registerForSession()

        assertThat(store.hasBootstrapped).isFalse()
        // Nothing is seeded from a registry that has never heard of this device.
        assertThat(repository.bootstraps).isEmpty()
        assertThat(store.pendingSeed).isNull()
    }

    @Test
    fun `registration is retried on the next launch after a failure`() = runTest {
        repository.registerResult = ApiResult.Failure(AppError.Network("offline"))
        val manager = manager()
        manager.registerForSession()

        repository.registerResult = ApiResult.Success(
            RegisteredDevice(
                deviceId = "android-this-phone",
                deviceName = "InterlinedList Android · Pixel 8",
                platform = "android",
                isDefault = true,
                lastSeenAt = null,
                appVersion = null,
                osVersion = null,
            ),
        )
        manager.registerForSession()

        assertThat(repository.registrations).hasSize(2)
    }

    // ---- bootstrap ---------------------------------------------------------

    @Test
    fun `a brand-new install seeds from the account's main workstation`() = runTest {
        repository.bootstrapResult = ApiResult.Success(seed())

        manager().registerForSession()

        assertThat(repository.bootstraps).containsExactly("android-this-phone")
        val adopted = store.pendingSeed
        assertThat(adopted?.source).isEqualTo(AppSettingsSeedSource.DEFAULT_DEVICE)
        assertThat(adopted?.sourceDeviceName).isEqualTo("Studio Pixel")
        // Adopted verbatim: #77 stores the document, #78 applies it.
        assertThat(adopted?.settings?.get("themeMode")?.jsonPrimitive?.content).isEqualTo("DARK")
        assertThat(store.hasBootstrapped).isTrue()
    }

    @Test
    fun `the account's first machine has nothing to seed from and never asks again`() = runTest {
        // 404 { "source": "none" }.
        repository.bootstrapResult = ApiResult.Failure(AppError.NotFound("Not found"))

        manager().registerForSession()

        assertThat(store.hasBootstrapped).isTrue()
        assertThat(store.pendingSeed).isNull()
    }

    @Test
    fun `an install that has already bootstrapped is not re-seeded`() = runTest {
        store.hasBootstrapped = true
        repository.bootstrapResult = ApiResult.Success(seed())

        manager().registerForSession()

        assertThat(repository.bootstraps).isEmpty()
        assertThat(store.pendingSeed).isNull()
    }

    @Test
    fun `a bootstrap that fails for any other reason is retried next launch`() = runTest {
        repository.bootstrapResult = ApiResult.Failure(AppError.Server("boom"))

        manager().registerForSession()

        assertThat(store.hasBootstrapped).isFalse()
        assertThat(store.pendingSeed).isNull()
    }

    // ---- sign-out / account deletion ---------------------------------------

    @Test
    fun `session teardown deregisters this device and forgets the account state`() = runTest {
        repository.bootstrapResult = ApiResult.Success(seed())
        val manager = manager()
        manager.registerForSession()

        // Exactly what DefaultAuthRepository.logout() invokes, through the same
        // SessionTeardownTask contract it runs for the push-token unregister.
        val task: SessionTeardownTask = AppDeviceSessionTeardown(manager)
        task.onSessionEnding()

        assertThat(repository.deregistrations).containsExactly("android-this-phone")
        assertThat(store.pendingSeed).isNull()
        assertThat(store.hasBootstrapped).isFalse()
    }

    @Test
    fun `signing in again after signing out registers again`() = runTest {
        val manager = manager()
        manager.registerForSession()
        AppDeviceSessionTeardown(manager).onSessionEnding()

        manager.registerForSession()

        assertThat(repository.registrations).hasSize(2)
        // Same phone, same id — the registry is scoped per user, so reusing it is right.
        assertThat(repository.registrations.map { it.deviceId }.distinct())
            .containsExactly("android-this-phone")
    }

    @Test
    fun `teardown of a never-registered install is harmless`() = runTest {
        // Account deletion immediately after a first, failed registration.
        repository.deregisterResult = ApiResult.Failure(AppError.NotFound("Not found"))

        AppDeviceSessionTeardown(manager()).onSessionEnding()

        assertThat(repository.deregistrations).hasSize(1)
        assertThat(store.hasBootstrapped).isFalse()
    }

    @Test
    fun `a failed deregistration does not stop the sign-out`() = runTest {
        repository.deregisterResult = ApiResult.Failure(AppError.Network("offline"))
        val manager = manager()
        manager.registerForSession()

        // Must not throw: a teardown step that blew up would be swallowed by
        // AuthRepository.logout() anyway, but it must not skip the local cleanup.
        AppDeviceSessionTeardown(manager).onSessionEnding()

        assertThat(store.hasBootstrapped).isFalse()
        assertThat(store.pendingSeed).isNull()
    }
}
