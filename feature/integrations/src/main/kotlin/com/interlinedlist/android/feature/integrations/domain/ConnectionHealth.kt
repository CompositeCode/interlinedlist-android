package com.interlinedlist.android.feature.integrations.domain

import java.time.Duration

/**
 * How trustworthy a linked account's authorization looks right now.
 *
 * The help centre (`/help/cross-posting` → *Keeping your connections active*)
 * explains that each platform authorization expires on its own schedule and has to
 * be renewed — LinkedIn's, for example, "generally expire after a couple of months
 * and can't be refreshed automatically". When one lapses, the next cross-post is
 * dropped by that platform without the composer ever saying so.
 *
 * The identities payload reports `lastVerifiedAt`, so the app can flag a connection
 * that has not been checked recently *before* a post silently fails to syndicate.
 */
enum class ConnectionHealth {
    /** Verified within [STALE_AFTER]; nothing to do. */
    FRESH,

    /** Verified, but longer ago than [STALE_AFTER] — it may already have lapsed. */
    STALE,

    /** Linked but never verified, so there is no evidence the authorization still works. */
    NEVER_VERIFIED,

    ;

    companion object {
        /**
         * A connection counts as [STALE] once it has gone unverified for this long.
         *
         * 30 days is half of the shortest documented expiry ("a couple of months" for
         * LinkedIn), which leaves roughly a month of warning before the earliest point
         * at which a cross-post could start failing, while staying quiet for anyone who
         * verifies or posts regularly.
         */
        val STALE_AFTER: Duration = Duration.ofDays(30)
    }
}
