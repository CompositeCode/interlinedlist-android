package com.interlinedlist.android.feature.messages.domain

/**
 * A single message in the social feed, normalised from the API wire model into a
 * platform-independent domain type. Mirrors the fields the web feed renders:
 * author identity + avatar, body content, a creation timestamp, and the dig /
 * reply engagement counts (plus whether the current user has dug it).
 */
data class Message(
    val id: String,
    val content: String,
    val authorId: String,
    val authorUsername: String,
    val authorDisplayName: String?,
    val authorAvatarUrl: String?,
    /** ISO-8601 creation instant, used to derive a relative timestamp for display. */
    val createdAt: String?,
    val digCount: Int,
    val replyCount: Int,
    /** Whether the signed-in user has dug this message (drives the dig toggle). */
    val dugByMe: Boolean,
    /** Parent message id when this is a reply; null for top-level feed messages. */
    val parentId: String?,
    /** True when this message belongs to the signed-in user (enables delete). */
    val mine: Boolean,
) {
    /** Best available display label for the author. */
    val authorLabel: String get() = authorDisplayName?.takeIf { it.isNotBlank() } ?: authorUsername
}
