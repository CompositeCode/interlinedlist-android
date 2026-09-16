package com.interlinedlist.android.core.network.preferences

import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.network.api.InterlinedListApi
import com.interlinedlist.android.core.network.error.safeApiCall
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the account's `notificationTrayLimit` — how many notifications the tray
 * holds — for the consumers that have to size themselves by it.
 *
 * It lives in `:core:network` for the same reason [ViewingPreferenceStore] does:
 * **no feature module in this repo depends on another feature module**, yet the
 * preference is written by `:feature:profile`'s Settings screen and read by
 * `:feature:notifications` (the in-app list's page size and the system-tray group
 * cap). A narrow accessor on the shared `GET /api/user` is the seam they can both
 * reach.
 *
 * The value is cached for the process so a poll or a pull-to-refresh does not pay
 * for an extra `GET /api/user` every time; `:feature:profile` calls [publish] after
 * every settings read or write, so a change made in Settings takes effect at once
 * instead of waiting for a restart.
 *
 * Consequence to be aware of: this is the **second** narrow account-preference
 * accessor in `:core:network`, sitting alongside `:feature:profile`'s full
 * `SettingsRepository`. Issue #104 ("Consolidate the two writers of account
 * preferences behind one owner") already tracks untangling that ownership, and this
 * store adds a reader to the same pile — so extend it rather than introducing a third
 * mechanism.
 */
@Singleton
class NotificationTrayLimitStore @Inject constructor(
    private val api: InterlinedListApi,
    private val json: Json,
) {

    /** Last known limit, or null until something reads or publishes one. */
    @Volatile
    private var cached: Int? = null

    /**
     * The limit to size the tray by: the cached value, otherwise a fresh read of
     * `GET /api/user`.
     *
     * A failed read answers [DEFAULT] and is deliberately **not** cached, so the next
     * caller retries — notifications must still load when the user endpoint is down,
     * but one blip must not pin the limit for the whole process.
     */
    suspend fun current(): Int {
        cached?.let { return it }
        return when (
            val result = safeApiCall(json) { api.getCurrentUser().user.notificationTrayLimit }
        ) {
            is ApiResult.Success -> publish(result.data)
            is ApiResult.Failure -> DEFAULT
        }
    }

    /**
     * Records the limit the server reports, clamped to [RANGE], and returns the value
     * that took effect. A null [limit] (the account has no stored value) resolves to
     * [DEFAULT].
     *
     * Clamping matters because the server's own range is the contract the tray is
     * sized by: a stored value from elsewhere must never make the list ask for a page
     * the endpoint would refuse.
     */
    fun publish(limit: Int?): Int =
        (limit?.coerceIn(RANGE) ?: DEFAULT).also { cached = it }

    companion object {
        /**
         * The limit a fresh account gets. `/help/settings`: "The default is 20 and you
         * can set any value from 10 to 40" — matching the live `GET /api/user` value.
         */
        const val DEFAULT: Int = 20

        /**
         * The values the server accepts, published in two places: `/help/settings`
         * ("any value from 10 to 40") and `/help/api/notifications` ("the user's
         * configured tray limit (default 20, clamped to 10-40)").
         */
        val RANGE: IntRange = 10..40
    }
}
