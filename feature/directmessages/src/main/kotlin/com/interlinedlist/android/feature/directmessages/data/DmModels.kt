package com.interlinedlist.android.feature.directmessages.data

/**
 * A conversation shown in the inbox — one row per `pairKey`, as returned by
 * `GET /api/dm/conversations`.
 */
data class Conversation(
    val username: String,
    val displayName: String?,
    val avatarUrl: String?,
    val lastMessageBody: String,
    val lastMessageAtMillis: Long,
    /** Unread received messages in this conversation, per the server. */
    val unreadCount: Int,
    val pairKey: String? = null,
) {
    /** Whether the inbox row should render its unread affordance. */
    val hasUnread: Boolean get() = unreadCount > 0
}

/** A single direct message shown in a thread. */
data class DirectMessage(
    val id: String,
    val conversationUsername: String,
    val senderId: String,
    val recipientId: String,
    val body: String,
    val imageUrls: List<String>,
    val createdAt: String,
    val createdAtMillis: Long,
    val readAt: String?,
    val pending: Boolean,
)

/** A person the current user can start a conversation with. */
data class Recipient(
    val id: String,
    val username: String,
    val displayName: String?,
    val avatarUrl: String?,
)
