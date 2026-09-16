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
     * Free-form labels attached to the message, exactly as the feed returned
     * them (`tags[]`). They may contain spaces and punctuation, so they are never
     * tokenised or normalised — only rendered.
     */
    val tags: List<String> = emptyList(),
    /**
     * ISO-8601 send time for a scheduled (not-yet-published) message; null for a
     * normal message. Present on rows returned by the scheduled endpoint.
     */
    val scheduledAt: String? = null,
    /**
     * ISO-8601 last-edited instant for a message whose content was changed after
     * it was posted; null for an un-edited message. Drives the "edited" indicator.
     */
    val editedAt: String? = null,
    /**
     * False when the message is private (visible only to its author). Public is
     * the server default, so an older cache row or a payload that omits the
     * field reads as public.
     */
    val publiclyVisible: Boolean = true,
    /** How many times this message has been pushed (reposted) by anyone. */
    val pushCount: Int = 0,
    /**
     * Id of the message this one re-shares, when it is a push or a quote; null
     * for an ordinary message. Sent as `pushedMessageId` on create.
     */
    val pushedMessageId: String? = null,
    /**
     * The re-shared original, embedded by the server on the message payload
     * (`pushedMessage`). Null when this message re-shares nothing — or, rarely,
     * when the server named [pushedMessageId] without embedding the original.
     */
    val pushedMessage: PushedMessage? = null,
) {
    /** Best available display label for the author. */
    val authorLabel: String get() = authorDisplayName?.takeIf { it.isNotBlank() } ?: authorUsername

    /** True when any image or video media is attached. */
    val hasMedia: Boolean get() = imageUrls.isNotEmpty() || videoUrls.isNotEmpty()

    /** True when the card should render a tag row. */
    val hasTags: Boolean get() = tags.isNotEmpty()

    /** True when the message has been edited since it was posted. */
    val isEdited: Boolean get() = editedAt != null

    /** [publiclyVisible] as a domain value. */
    val visibility: MessageVisibility get() = MessageVisibility.of(publiclyVisible)

    /**
     * True when the card should mark this message as private. Only the author's
     * own private messages are marked — public messages are never badged, and
     * another user's message is never labelled on the viewer's behalf.
     */
    val showsPrivateBadge: Boolean get() = mine && !publiclyVisible

    /** True when this message re-shares another one: a push or a quote. */
    val isReshare: Boolean get() = pushedMessageId != null || pushedMessage != null

    /** A **push** (repost): re-shares the original as-is, with no comment added. */
    val isPush: Boolean get() = isReshare && content.isBlank()

    /** A **quote**: re-shares the original with the author's own note attached. */
    val isQuote: Boolean get() = isReshare && content.isNotBlank()

    /**
     * Whether Push and Quote may be offered for this message. Both actions post a
     * `pushedMessageId`, so they share one rule, drawn from the docs:
     *
     * - `/help/messages` describes both as re-sharing **someone else's** message,
     *   so your own message is not pushable;
     * - `/help/api/messages` defines `pushedMessageId` as "repost this **public**
     *   message ID", so a private message is not pushable;
     * - neither page defines a push-of-a-push, so re-shares are not re-shared —
     *   the original is what deserves the amplification, not a wrapper around it.
     */
    val canBePushed: Boolean get() = !mine && publiclyVisible && !isReshare
}

/**
 * The original message embedded inside a push or a quote. The feed payload nests
 * it as `pushedMessage`, so the card can render the re-shared post without a
 * second fetch. Deliberately narrower than [Message]: only what the inset card
 * shows. Tap it to open the original's own page.
 */
data class PushedMessage(
    val id: String,
    val content: String,
    val authorUsername: String,
    val authorDisplayName: String?,
    val authorAvatarUrl: String?,
    /** ISO-8601 creation instant of the original. */
    val createdAt: String?,
) {
    /** Best available display label for the original's author. */
    val authorLabel: String get() = authorDisplayName?.takeIf { it.isNotBlank() } ?: authorUsername
}

/** Narrows a full [Message] to the compact form embedded in a push or quote. */
fun Message.asPushedOriginal(): PushedMessage = PushedMessage(
    id = id,
    content = content,
    authorUsername = authorUsername,
    authorDisplayName = authorDisplayName,
    authorAvatarUrl = authorAvatarUrl,
    createdAt = createdAt,
)

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
