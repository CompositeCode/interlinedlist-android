package com.interlinedlist.android.feature.directmessages.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Response for `GET /api/dm/conversations` — one row per conversation, grouped
 * by `pairKey`, newest activity first, cursor-paginated by `nextCursor`.
 *
 * The response body is unmodelled in the OpenAPI spec, so the list is accepted
 * under `items` (the convention every other cursor-paginated endpoint uses) or
 * under a named `conversations` key (the convention `recipients`/`notifications`
 * use).
 */
@Serializable
data class ConversationsResponse(
    val items: List<ConversationDto> = emptyList(),
    val conversations: List<ConversationDto> = emptyList(),
    val nextCursor: String? = null,
) {
    /** The conversation rows under whichever key the endpoint used. */
    val rows: List<ConversationDto> get() = items.ifEmpty { conversations }
}

/**
 * Response for `GET /api/dm/thread/{username}`.
 *
 * Confirmed live shape: `{ items, olderCursor, isMutual, isBlocked, otherUser }`.
 */
@Serializable
data class ThreadResponse(
    val items: List<MessageDto> = emptyList(),
    val olderCursor: String? = null,
    val isMutual: Boolean = false,
    val isBlocked: Boolean = false,
    val otherUser: RecipientDto? = null,
)

/**
 * Response for `GET /api/dm/thread/{username}/updates`.
 *
 * Confirmed live shape: `{ "items": [...] }` — the messages newer than the
 * caller's `after` cursor.
 */
@Serializable
data class ThreadUpdatesResponse(
    val items: List<MessageDto> = emptyList(),
)

/** Response for `GET /api/dm/unread-count`: `{ "count": 0 }`. */
@Serializable
data class UnreadCountResponse(
    val count: Int = 0,
)

/**
 * Request for `POST /api/dm`.
 *
 * The recipient may be addressed by id or username; both are sent when known so
 * the server can resolve either way.
 */
@Serializable
data class SendMessageRequest(
    val recipientId: String? = null,
    val recipientUsername: String? = null,
    val body: String,
    val imageUrls: List<String> = emptyList(),
)

/**
 * Response for `POST /api/dm`. The documented shape wraps the created message
 * under `message`; other create endpoints wrap it under `data`. Both envelopes
 * and the bare message are accepted.
 */
@Serializable
data class SendMessageResponse(
    val id: String? = null,
    val senderId: String? = null,
    val recipientId: String? = null,
    val body: String? = null,
    val imageUrls: List<String> = emptyList(),
    val createdAt: String? = null,
    val message: MessageDto? = null,
    val data: MessageDto? = null,
) {
    /** The created message, unwrapping the `message`/`data` envelope when present. */
    fun createdMessage(): MessageDto? = message ?: data ?: id?.let {
        MessageDto(
            id = it,
            senderId = senderId ?: "",
            recipientId = recipientId ?: "",
            body = body ?: "",
            imageUrls = imageUrls,
            createdAt = createdAt ?: "",
        )
    }
}

/** Response for `POST /api/dm/images/upload`: the hosted URL of the attachment. */
@Serializable
data class ImageUploadResponse(
    val url: String? = null,
    val imageUrl: String? = null,
    val data: ImageUploadData? = null,
) {
    /** The uploaded image URL under whichever key the endpoint used. */
    fun resolvedUrl(): String? = url ?: imageUrl ?: data?.url
}

@Serializable
data class ImageUploadData(
    val url: String? = null,
)
