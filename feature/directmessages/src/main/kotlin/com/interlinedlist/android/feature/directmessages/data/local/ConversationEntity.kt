package com.interlinedlist.android.feature.directmessages.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A conversation row from `GET /api/dm/conversations`, cached as the inbox's
 * source of truth. Distinct from [DirectMessageEntity]: this is the server's
 * per-conversation summary, not a message.
 *
 * Keyed by the other participant's username because every thread-scoped
 * operation in this module (thread fetch, polling, navigation) is addressed by
 * username; [pairKey] keeps the server's own grouping anchor alongside it.
 */
@Entity(tableName = "dm_conversation")
data class ConversationEntity(
    @PrimaryKey val username: String,
    val pairKey: String?,
    val displayName: String?,
    val avatarUrl: String?,
    val lastMessageId: String,
    val lastMessageBody: String,
    val lastMessageAtMillis: Long,
    /** Unread received messages in this conversation, as reported by the server. */
    val unreadCount: Int,
)
