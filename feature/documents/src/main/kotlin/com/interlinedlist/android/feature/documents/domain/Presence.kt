package com.interlinedlist.android.feature.documents.domain

/**
 * A single participant currently viewing a document, derived from the server's
 * lightweight presence heartbeats. Full live-cursor sync is out of scope — we only
 * surface "who is here" as avatars, so cursor offsets are intentionally omitted.
 */
data class Presence(
    val userId: String,
    val displayName: String?,
    val username: String?,
    val avatarUrl: String?,
) {
    val label: String
        get() = displayName?.takeIf { it.isNotBlank() } ?: username?.takeIf { it.isNotBlank() } ?: userId

    val initial: String get() = label.trim().firstOrNull()?.uppercase() ?: "?"
}
