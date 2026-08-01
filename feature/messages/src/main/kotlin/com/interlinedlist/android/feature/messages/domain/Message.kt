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
    /** Attached image URLs, rendered inline in the feed/detail. */
    val imageUrls: List<String> = emptyList(),
    /** Attached video URLs, rendered as a tappable thumbnail/placeholder. */
    val videoUrls: List<String> = emptyList(),
    /** Link-preview card built from fetched metadata, when present. */
    val linkPreview: LinkPreview? = null,
    /**
     * ISO-8601 send time for a scheduled (not-yet-published) message; null for a
     * normal message. Present on rows returned by the scheduled endpoint.
     */
    val scheduledAt: String? = null,
) {
    /** Best available display label for the author. */
    val authorLabel: String get() = authorDisplayName?.takeIf { it.isNotBlank() } ?: authorUsername

    /** True when any image or video media is attached. */
    val hasMedia: Boolean get() = imageUrls.isNotEmpty() || videoUrls.isNotEmpty()
}

/**
 * Link-preview metadata for the first URL found in a message, fetched via the
 * metadata endpoint and rendered as a card. All fields are best-effort; a preview
 * is only shown when at least a [url] and a [title] are available.
 */
data class LinkPreview(
    val url: String,
    val title: String?,
    val description: String?,
    val imageUrl: String?,
    val siteName: String?,
)
