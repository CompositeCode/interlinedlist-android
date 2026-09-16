package com.interlinedlist.android.feature.integrations.domain

import java.time.Duration
import java.time.Instant

/**
 * A social/identity provider the user can cross-post to and, when the account is
 * actually linked, the identity record standing behind it.
 *
 * *Linking* still needs a browser OAuth redirect and stays on the web. *Unlinking*
 * and *re-verifying* an already-linked account are plain API calls, both keyed on
 * [identityProvider] — the raw `provider` string the identities payload returns
 * (`github`, `linkedin`, `mastodon:techhub.social`, …), which
 * `DELETE /api/user/identities?provider=` and `POST /api/user/identities/verify`
 * both expect verbatim. A null [identityProvider] means nothing is linked for this
 * provider, so there is nothing to unlink or verify.
 *
 * Timestamps stay as the API's raw ISO-8601 strings (the repo convention); the
 * derived state comes from [healthAt], which takes [Instant] so rendering stays
 * deterministic in tests.
 */
data class ConnectedAccount(
    val provider: Provider,
    val isConnected: Boolean,
    /** Provider-supplied handle/username when connected, e.g. "@you". */
    val handle: String? = null,
    /** The identity's `provider` string, which unlink/verify key on; null when unlinked. */
    val identityProvider: String? = null,
    /** ISO-8601 instant the account was linked, when the API reports one. */
    val connectedAt: String? = null,
    /** ISO-8601 instant the connection was last confirmed to work, when the API reports one. */
    val lastVerifiedAt: String? = null,
) {
    /** True when there is an identity record behind this row, i.e. unlink/verify are possible. */
    val isLinked: Boolean get() = !identityProvider.isNullOrBlank()

    /**
     * Stable list key. One provider can back several identities — Mastodon returns
     * one per instance (`mastodon:techhub.social`) — so the identity string, not the
     * provider, identifies a row.
     */
    val key: String get() = identityProvider?.takeIf { it.isNotBlank() } ?: provider.name

    /**
     * Connection health at [now], or null when nothing is linked (health is a property
     * of an authorization, and an unlinked provider has none).
     *
     * An unparseable `lastVerifiedAt` is treated as never verified rather than fresh:
     * the point of the badge is to fail loud rather than let a syndication fail quiet.
     */
    fun healthAt(now: Instant = Instant.now()): ConnectionHealth? {
        if (!isLinked) return null
        val verified = lastVerifiedAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
            ?: return ConnectionHealth.NEVER_VERIFIED
        val age = Duration.between(verified, now)
        return if (age >= ConnectionHealth.STALE_AFTER) ConnectionHealth.STALE else ConnectionHealth.FRESH
    }

    enum class Provider(
        val statusPath: String,
        val label: String,
        /** The token the identities API uses; a Mastodon identity prefixes it to `:host`. */
        val apiToken: String,
        /** True when unlinking this account stops cross-posting to a social network. */
        val isCrossPostTarget: Boolean,
    ) {
        GITHUB("api/auth/github/status", "GitHub", "github", isCrossPostTarget = false),
        LINKEDIN("api/auth/linkedin/status", "LinkedIn", "linkedin", isCrossPostTarget = true),
        BLUESKY("api/auth/bluesky/status", "Bluesky", "bluesky", isCrossPostTarget = true),
        MASTODON("api/auth/mastodon/status", "Mastodon", "mastodon", isCrossPostTarget = true),
        TWITTER("api/auth/twitter/status", "X (Twitter)", "twitter", isCrossPostTarget = true),
        ;

        companion object {
            /**
             * The provider an identity's `provider` string belongs to, or null when it is
             * one this screen does not model. Mastodon encodes the instance after a colon,
             * so only the leading token is matched.
             */
            fun fromIdentityProvider(raw: String?): Provider? {
                val token = raw?.substringBefore(':')?.trim()?.lowercase().orEmpty()
                return entries.firstOrNull { it.apiToken == token }
            }
        }
    }
}
