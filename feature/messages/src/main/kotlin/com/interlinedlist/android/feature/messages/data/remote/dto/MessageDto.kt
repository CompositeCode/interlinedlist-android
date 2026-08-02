package com.interlinedlist.android.feature.messages.data.remote.dto

import com.interlinedlist.android.feature.messages.domain.LinkPreview
import com.interlinedlist.android.feature.messages.domain.Message
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
    /** Future send time for a scheduled message; null once published. */
    val scheduledAt: String? = null,
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
        digCount = digCount,
        replyCount = replyCount,
        dugByMe = dugByCurrentUser,
        parentId = parentId,
        mine = isOwn || (currentUserId != null && person?.id == currentUserId),
        imageUrls = imageUrls,
        videoUrls = videoUrls,
        linkPreview = linkMetadata?.toDomain(),
        scheduledAt = scheduledAt,
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
