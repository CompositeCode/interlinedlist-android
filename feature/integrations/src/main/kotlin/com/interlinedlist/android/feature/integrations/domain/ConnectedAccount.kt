package com.interlinedlist.android.feature.integrations.domain

/**
 * A social/identity provider the user can connect on the web, and whether the
 * current account is connected. This module is read-only: the OAuth "connect"
 * dance needs a browser redirect and is deferred to the web app, so the UI only
 * surfaces status plus a "manage on the web" note.
 */
data class ConnectedAccount(
    val provider: Provider,
    val isConnected: Boolean,
    /** Provider-supplied handle/username when connected, e.g. "@you". */
    val handle: String? = null,
) {
    enum class Provider(val statusPath: String, val label: String) {
        GITHUB("api/auth/github/status", "GitHub"),
        LINKEDIN("api/auth/linkedin/status", "LinkedIn"),
        BLUESKY("api/auth/bluesky/status", "Bluesky"),
        MASTODON("api/auth/mastodon/status", "Mastodon"),
        TWITTER("api/auth/twitter/status", "X (Twitter)"),
    }
}
