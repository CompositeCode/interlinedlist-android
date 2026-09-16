package com.interlinedlist.android.feature.messages.data.remote.dto

import com.interlinedlist.android.feature.messages.domain.LinkPreview
import com.interlinedlist.android.feature.messages.domain.Message
import com.interlinedlist.android.feature.messages.domain.PushedMessage
import kotlinx.serialization.Serializable

/**
 * Wire model for a message returned by the Messages endpoints.
 *
 * The OpenAPI extract does not pin the response schema, so this mirrors the web
 * feed's shape: an author sub-object plus body/timestamp/engagement fields. The
 * shared [kotlinx.serialization.json.Json] is configured with `ignoreUnknownKeys`,
 * so extra fields (crossposting, …) are tolerated and dropped.
 */
@Serializable
data class MessageDto(
    val id: String,
    val content: String = "",
    val author: MessageAuthorDto? = null,
    /** The create/detail endpoints key the author sub-object as `user` rather than
     *  `author`; [toDomain] falls back to whichever the payload used. */
    val user: MessageAuthorDto? = null,
    val createdAt: String? = null,
    /** Last-modified instant; when it differs from [createdAt] the message was edited. */
    val updatedAt: String? = null,
    val digCount: Int = 0,
    val replyCount: Int = 0,
    val dugByCurrentUser: Boolean = false,
    val parentId: String? = null,
    /** Present on some payloads; used to flag the message as the caller's own. */
    val isOwn: Boolean = false,
    /** Attached media (uploaded via the image/video upload endpoints). */
    val imageUrls: List<String> = emptyList(),
    val videoUrls: List<String> = emptyList(),
    /** Fetched link-preview metadata for the first URL in the body, if any. */
    val linkMetadata: LinkMetadataDto? = null,
    /**
     * Free-form tags on the message. The feed already carries these, so the card
     * renders them without a second fetch. Values may contain spaces and
     * punctuation — never split or normalise them.
     */
    val tags: List<String> = emptyList(),
    /** Future send time for a scheduled message; null once published. */
    val scheduledAt: String? = null,
    /** False when the message is private (visible only to its author). */
    val publiclyVisible: Boolean = true,
    /** How many times this message has been pushed (reposted). */
    val pushCount: Int = 0,
    /** Id of the message this one re-shares (push or quote); null otherwise. */
    val pushedMessageId: String? = null,
    /**
     * The re-shared original, embedded by the server. Null when this message is
     * not a push/quote — so the feed renders the original without a second fetch.
     */
    val pushedMessage: PushedMessageDto? = null,
)

/** Author identity embedded in a message. */
@Serializable
data class MessageAuthorDto(
    val id: String = "",
    val username: String = "",
    val displayName: String? = null,
    val avatar: String? = null,
)

/**
 * The original message nested under `pushedMessage` on a push or a quote.
 *
 * Only the fields the inset "original" card renders are modelled: the server
 * sends a full message object here, and `ignoreUnknownKeys` drops the rest. Like
 * the outer message, the author arrives as either `user` or `author`.
 */
@Serializable
data class PushedMessageDto(
    val id: String = "",
    val content: String = "",
    val user: MessageAuthorDto? = null,
    val author: MessageAuthorDto? = null,
    val createdAt: String? = null,
)

/**
 * Link-preview metadata attached to a message. Populated by the metadata endpoint;
 * mirrors the OpenGraph-style fields the web feed renders in its preview card.
 */
@Serializable
data class LinkMetadataDto(
    val url: String? = null,
    val title: String? = null,
    val description: String? = null,
    val image: String? = null,
    val siteName: String? = null,
)

/**
 * Maps the wire model into the domain [Message]. [currentUserId] lets us flag
 * the caller's own messages (for delete) even when the API omits `isOwn`.
 */
fun MessageDto.toDomain(currentUserId: String?): Message {
    // The API is inconsistent about the author key: the feed uses `author`, the
    // create/detail endpoints use `user`. Prefer whichever the payload populated.
    val person = author ?: user
    return Message(
        id = id,
        content = content,
        authorId = person?.id.orEmpty(),
        authorUsername = person?.username.orEmpty(),
        authorDisplayName = person?.displayName,
        authorAvatarUrl = person?.avatar,
        createdAt = createdAt,
        // Treat the message as edited only when it was modified after creation.
        editedAt = updatedAt?.takeIf { createdAt == null || it != createdAt },
        digCount = digCount,
        replyCount = replyCount,
        dugByMe = dugByCurrentUser,
        parentId = parentId,
        mine = isOwn || (currentUserId != null && person?.id == currentUserId),
        imageUrls = imageUrls,
        videoUrls = videoUrls,
        linkPreview = linkMetadata?.toDomain(),
        // Kept verbatim: a tag is a label, not a token.
        tags = tags,
        scheduledAt = scheduledAt,
        publiclyVisible = publiclyVisible,
        pushCount = pushCount,
        // The embedded original is authoritative for the id when the flat field
        // is absent: either one makes this a push/quote.
        pushedMessageId = pushedMessageId?.takeIf { it.isNotBlank() }
            ?: pushedMessage?.id?.takeIf { it.isNotBlank() },
        pushedMessage = pushedMessage?.toDomainOrNull(),
    )
}

/** Maps the embedded original, dropping an entry the server sent without an id. */
fun PushedMessageDto.toDomainOrNull(): PushedMessage? {
    val originalId = id.takeIf { it.isNotBlank() } ?: return null
    val person = author ?: user
    return PushedMessage(
        id = originalId,
        content = content,
        authorUsername = person?.username.orEmpty(),
        authorDisplayName = person?.displayName,
        authorAvatarUrl = person?.avatar,
        createdAt = createdAt,
    )
}

/** Maps link-preview metadata into the domain, dropping empty previews. */
fun LinkMetadataDto.toDomain(): LinkPreview? {
    val link = url?.takeIf { it.isNotBlank() } ?: return null
    // A preview with no title/description/image carries no useful content.
    if (title.isNullOrBlank() && description.isNullOrBlank() && image.isNullOrBlank()) return null
    return LinkPreview(
        url = link,
        title = title,
        description = description,
        imageUrl = image,
        siteName = siteName,
    )
}
