package com.interlinedlist.android.feature.notifications.data

import com.interlinedlist.android.core.common.result.ApiResult

/**
 * The device-token half of push notifications: tells the server which device the
 * signed-in account's pushes should go to, and — just as importantly — that they
 * should stop.
 */
interface PushRegistrationRepository {

    /**
     * Registers (or updates) [token] for the signed-in user as an `android` device.
     * Re-registering a token the server already knows updates the existing record, so
     * this is safe to call on every launch.
     */
    suspend fun register(token: String): ApiResult<Unit>

    /**
     * Removes [token]'s registration. Idempotent — an unknown token still succeeds —
     * so it can be called defensively on sign-out without tracking what was registered.
     */
    suspend fun unregister(token: String): ApiResult<Unit>
}
