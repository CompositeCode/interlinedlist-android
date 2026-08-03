package com.interlinedlist.android.feature.directmessages.data

/** A conversation summary shown in the inbox list. */
data class Conversation(
    val username: String,
    val displayName: String?,
    val avatarUrl: String?,
    val lastMessageBody: String,
    val lastMessageAtMillis: Long,
    val hasUnread: Boolean,
)

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
