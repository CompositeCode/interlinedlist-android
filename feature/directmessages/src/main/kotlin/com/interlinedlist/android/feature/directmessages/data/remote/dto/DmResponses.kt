package com.interlinedlist.android.feature.directmessages.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Response for `GET /api/dm` (inbox/sent folders).
 *
 * Confirmed live shape: `{ "items": [...], "nextCursor": null }`. Each item is a
 * message; the last message of a conversation represents that conversation in
 * the inbox folder.
 */
@Serializable
data class InboxResponse(
    val items: List<MessageDto> = emptyList(),
    val nextCursor: String? = null,
)

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
 * Response for `POST /api/dm`. Some create endpoints wrap the created object
 * under `data`; accept both the bare message and the wrapped form.
 */
@Serializable
data class SendMessageResponse(
    val id: String? = null,
    val senderId: String? = null,
    val recipientId: String? = null,
    val body: String? = null,
    val imageUrls: List<String> = emptyList(),
    val createdAt: String? = null,
    val data: MessageDto? = null,
) {
    /** The created message, unwrapping the `data` envelope when present. */
    fun message(): MessageDto? = data ?: id?.let {
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
