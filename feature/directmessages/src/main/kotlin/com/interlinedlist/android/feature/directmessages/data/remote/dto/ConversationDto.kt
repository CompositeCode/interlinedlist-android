package com.interlinedlist.android.feature.directmessages.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Wire model for one row of `GET /api/dm/conversations` — a conversation
 * grouped by `pairKey`, newest activity first. This is the inbox's data source;
 * it is deliberately *not* the message DTO.
 *
 * The OpenAPI spec declares this response as a bare `object` and the public help
 * centre does not document it, so the row is modelled defensively: every field
 * is optional, and the keys this API is known to use interchangeably are all
 * accepted — the other participant as `otherUser` (as on the thread endpoint),
 * `user` or `author` (the documented envelope quirk); the newest message either
 * nested under `lastMessage`/`message` or flattened onto the row. Unknown keys
 * are ignored by the shared Json configuration.
 */
@Serializable
data class ConversationDto(
    /** Stable, sorted `a:b` anchor identifying the conversation server-side. */
    val pairKey: String? = null,
    // The other participant, under whichever key the endpoint used.
    val otherUser: RecipientDto? = null,
    val user: RecipientDto? = null,
    val author: RecipientDto? = null,
    // The newest message, either nested...
    val lastMessage: MessageDto? = null,
    val message: MessageDto? = null,
    // ...or flattened onto the row itself.
    val lastMessageId: String? = null,
    val lastMessageBody: String? = null,
    val preview: String? = null,
    val body: String? = null,
    val lastMessageAt: String? = null,
    val updatedAt: String? = null,
    val createdAt: String? = null,
    val readAt: String? = null,
    // Unread bookkeeping; `unreadCount` is per conversation, not per folder.
    val unreadCount: Int? = null,
    val hasUnread: Boolean? = null,
) {
    /** The conversation partner under whichever key the endpoint used. */
    val participant: RecipientDto? get() = otherUser ?: user ?: author

    /** The newest message of the conversation when the row nests one. */
    val newestMessage: MessageDto? get() = lastMessage ?: message

    /** Plain-text excerpt to render on the inbox row. */
    val previewText: String
        get() = (preview ?: lastMessageBody ?: body ?: newestMessage?.body).orEmpty()

    /** ISO-8601 timestamp of the conversation's newest activity. */
    val lastActivityAt: String?
        get() = lastMessageAt ?: newestMessage?.createdAt ?: updatedAt ?: createdAt

    /** Id of the newest message, when the row identifies one. */
    val newestMessageId: String get() = (lastMessageId ?: newestMessage?.id).orEmpty()
}
