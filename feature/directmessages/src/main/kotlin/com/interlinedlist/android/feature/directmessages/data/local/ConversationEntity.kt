package com.interlinedlist.android.feature.directmessages.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A per-conversation summary backing the inbox list. Keyed by the other
 * participant's username so it aligns with the thread endpoints.
 */
@Entity(tableName = "dm_conversation")
data class ConversationEntity(
    @PrimaryKey val username: String,
    val displayName: String?,
    val avatarUrl: String?,
    val lastMessageId: String,
    val lastMessageBody: String,
    val lastMessageAtMillis: Long,
    /** Whether the latest received message in this conversation is unread. */
    val hasUnread: Boolean,
)
