package com.interlinedlist.android.feature.notifications.push

import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.notifications.data.PushRegistrationRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the device-token lifecycle. It is the only place that decides WHEN this device
 * is registered for push and when that registration is torn down.
 *
 * Lifecycle, in the order the server contract asks for it:
 * - **first availability + rotation** — [runForSession] collects [PushTokenProvider.token]
 *   for as long as a signed-in session is on screen, so a token that appears late or
 *   changes is registered as soon as it does;
 * - **every app launch** — the provider's flow is a `StateFlow`, so collection replays
 *   the current token immediately; a fresh process has registered nothing yet and
 *   therefore re-registers, exactly as the docs require ("re-register on every app
 *   launch in case the token rotated");
 * - **sign-out and account deletion** — [unregisterCurrentToken], driven from
 *   `:feature:auth`'s single sign-out path via [PushTokenSessionTeardown].
 *
 * Registration is additionally gated on the device actually being able to show a
 * notification ([NotificationPermissionChecker]): there is no point asking the server
 * to push to a device whose notifications are switched off, and the app stays fully
 * functional in that state (the in-app tray and the WorkManager poll are untouched).
 * [onNotificationPermissionGranted] closes the loop when the user grants it later.
 */
@Singleton
class PushRegistrationManager @Inject constructor(
    private val tokenProvider: PushTokenProvider,
    private val repository: PushRegistrationRepository,
    private val permissions: NotificationPermissionChecker,
) {

    /** Guards [registeredToken] against concurrent rotation/teardown. */
    private val mutex = Mutex()

    /**
     * The token this process last registered successfully. Kept so sign-out can retire
     * it even if the provider has since rotated to a different one.
     */
    private var registeredToken: String? = null

    /**
     * Runs the registration lifecycle for a signed-in session. Suspends until the
     * caller's scope is cancelled (i.e. until sign-out), registering the current token
     * immediately and every rotation thereafter.
     */
    suspend fun runForSession() {
        tokenProvider.token.collect { token -> registerIfPossible(token) }
    }

    /**
     * Re-attempts registration right after POST_NOTIFICATIONS is granted, since the
     * token itself has not changed and so will not be re-emitted.
     */
    suspend fun onNotificationPermissionGranted() {
        registerIfPossible(tokenProvider.token.value)
    }

    /**
     * Retires this device's registration so notifications for the account being signed
     * out of can no longer reach it — the security-relevant half of the lifecycle.
     *
     * Both the token this process registered and the provider's current token are
     * retired. They normally coincide — a rotation retires the token it supersedes
     * straight away — but they diverge if a registration failed, and unregister is
     * idempotent, so the redundant call is harmless where a missed one is not.
     */
    suspend fun unregisterCurrentToken() {
        val tokens = mutex.withLock {
            val pending = setOfNotNull(
                registeredToken?.takeIf { it.isNotBlank() },
                tokenProvider.token.value?.takeIf { it.isNotBlank() },
            )
            registeredToken = null
            pending
        }
        tokens.forEach { repository.unregister(it) }
    }

    private suspend fun registerIfPossible(token: String?) {
        if (token.isNullOrBlank()) return
        // Denied (or switched off in system settings): stay silent. No registration is
        // issued and nothing else in the app is affected.
        if (!permissions.canPostNotifications()) return
        val previous = mutex.withLock { registeredToken }
        if (token == previous) return

        if (repository.register(token) !is ApiResult.Success) return
        mutex.withLock { registeredToken = token }
        // The token rotated: retire the registration it replaced, rather than leaving
        // the server holding a dead one for this device. Done only after the new
        // registration succeeded, so a failure never leaves the device unreachable.
        if (previous != null) repository.unregister(previous)
    }
}
