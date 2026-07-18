package com.interlinedlist.android.feature.messages.data.remote.dto

import com.interlinedlist.android.feature.messages.domain.Message
import kotlinx.serialization.Serializable

/**
 * Wire model for a message returned by the Messages endpoints.
 *
 * The OpenAPI extract does not pin the response schema, so this mirrors the web
 * feed's shape: an author sub-object plus body/timestamp/engagement fields. The
 * shared [kotlinx.serialization.json.Json] is configured with `ignoreUnknownKeys`,
 * so extra fields (crossposting, metadata, media, …) are tolerated and dropped.
 */
@Serializable
data class MessageDto(
    val id: String,
    val content: String = "",
    val author: MessageAuthorDto? = null,
    val createdAt: String? = null,
    val digCount: Int = 0,
    val replyCount: Int = 0,
    val dugByCurrentUser: Boolean = false,
    val parentId: String? = null,
    /** Present on some payloads; used to flag the message as the caller's own. */
    val isOwn: Boolean = false,
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
 * Maps the wire model into the domain [Message]. [currentUserId] lets us flag
 * the caller's own messages (for delete) even when the API omits `isOwn`.
 */
fun MessageDto.toDomain(currentUserId: String?): Message = Message(
    id = id,
    content = content,
    authorId = author?.id.orEmpty(),
    authorUsername = author?.username.orEmpty(),
    authorDisplayName = author?.displayName,
    authorAvatarUrl = author?.avatar,
    createdAt = createdAt,
    digCount = digCount,
    replyCount = replyCount,
    dugByMe = dugByCurrentUser,
    parentId = parentId,
    mine = isOwn || (currentUserId != null && author?.id == currentUserId),
)
