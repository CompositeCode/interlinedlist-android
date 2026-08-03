package com.interlinedlist.android.feature.profile.domain

/**
 * A social account linked to the current user (e.g. `mastodon:host`, `linkedin`,
 * `twitter`, `bluesky`), shown on the Connected Accounts screen. Unlinking keys on
 * [provider], which the API's `DELETE /api/user/identities?provider=...` expects.
 *
 * [connectedAt] is kept as the raw ISO-8601 string the API returns; the UI layer
 * formats it for display.
 */
data class LinkedIdentity(
    val id: String,
    val provider: String,
    val providerUsername: String?,
    val profileUrl: String?,
    val avatarUrl: String?,
    val connectedAt: String?,
) {
    /**
     * A human-friendly provider name. Mastodon identities encode the host as
     * `mastodon:techhub.social`; only the leading provider token is shown, title-cased.
     */
    val providerLabel: String
        get() {
            val token = provider.substringBefore(':').takeIf { it.isNotBlank() } ?: provider
            return token.replaceFirstChar { it.uppercase() }
        }

    /** The best label for the linked account: username if set, else the provider name. */
    val displayLabel: String
        get() = providerUsername?.takeIf { it.isNotBlank() } ?: providerLabel
}
