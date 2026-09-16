package com.interlinedlist.android.core.appsettings

import com.interlinedlist.android.core.appsettings.data.AppSettingsRepository
import com.interlinedlist.android.core.appsettings.device.AppDeviceStore
import com.interlinedlist.android.core.appsettings.domain.ClientVersions
import com.interlinedlist.android.core.common.device.DeviceLabelProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns this install's presence in the account's companion-app device registry — the
 * only place that decides WHEN Android registers itself under Settings → Applications
 * and when that registration is retired.
 *
 * Lifecycle:
 * - **first sign-in, and every launch while signed in** — [registerForSession], driven
 *   from the signed-in shell exactly like the push-token registration. Entering the
 *   shell is precisely "just signed in" or "launched signed in", and the endpoint is
 *   register-*or-refresh*, so the repeat call is what keeps `lastSeenAt` honest.
 * - **brand-new install** — the first successful registration is followed by a
 *   bootstrap read, which the web documents as seeding a new machine from the account's
 *   main workstation. Runs at most once per account per install.
 * - **sign-out and account deletion** — [deregisterOnSessionEnding], driven from
 *   `:feature:auth`'s single sign-out path via [AppDeviceSessionTeardown].
 *
 * Nothing here is on the sign-in critical path and nothing here throws: every call
 * returns [ApiResult], so a failed registration leaves the user signed in and simply
 * retries on the next launch.
 */
@Singleton
class AppDeviceRegistrationManager @Inject constructor(
    private val repository: AppSettingsRepository,
    private val store: AppDeviceStore,
    private val deviceLabels: DeviceLabelProvider,
    private val clientVersions: ClientVersions,
) {

    /** Serialises registration against a concurrent sign-out teardown. */
    private val mutex = Mutex()

    /**
     * True once this process has registered successfully for the current session, so
     * re-entering the shell (tab changes, configuration changes) does not re-POST.
     * Reset by [deregisterOnSessionEnding] so a subsequent sign-in registers again.
     */
    private var registeredThisSession = false

    /**
     * Registers this device for the signed-in session and, on a brand-new install,
     * seeds it from the account. Safe to call on every entry to the signed-in shell.
     */
    suspend fun registerForSession() {
        mutex.withLock {
            if (registeredThisSession) return
            val deviceId = store.deviceId()
            val registered = repository.registerDevice(
                deviceId = deviceId,
                deviceName = deviceLabels.deviceLabel,
                appVersion = clientVersions.appVersion,
                osVersion = clientVersions.osVersion,
            )
            // Offline or rejected: stay unregistered and retry on the next launch.
            // Bootstrapping now would ask the registry about a device it has never
            // heard of, so it waits too.
            if (registered !is ApiResult.Success) return
            registeredThisSession = true
            bootstrapIfFreshInstall(deviceId)
        }
    }

    /**
     * Retires this device's registration (and, server-side, its device-scoped settings
     * document) so the account being left no longer lists a phone it can no longer
     * reach, then forgets the account-specific state kept here. Called while the bearer
     * token is still persisted — see [AppDeviceSessionTeardown].
     */
    suspend fun deregisterOnSessionEnding() {
        mutex.withLock {
            // The network call goes FIRST: it is bearer-authed, and the local state is
            // what lets a retry find the right device.
            repository.deregisterDevice(store.deviceId())
            store.clearAccountState()
            registeredThisSession = false
        }
    }

    /**
     * Adopts what the account says a fresh machine should start from. The document is
     * stored verbatim in [AppDeviceStore.pendingSeed] and **not** interpreted here:
     * applying it to the device's preferences is #78's job, and this is the seam it
     * plugs into.
     */
    private suspend fun bootstrapIfFreshInstall(deviceId: String) {
        if (store.hasBootstrapped) return
        when (val result = repository.bootstrap(deviceId)) {
            is ApiResult.Success -> {
                store.pendingSeed = result.data
                store.hasBootstrapped = true
            }
            is ApiResult.Failure -> {
                // 404 == `{ "source": "none" }`: this is the account's first machine,
                // so there is nothing to seed from and nothing to ask for again.
                // Anything else (offline, 5xx) stays pending for the next launch.
                if (result.error is AppError.NotFound) store.hasBootstrapped = true
            }
        }
    }
}
