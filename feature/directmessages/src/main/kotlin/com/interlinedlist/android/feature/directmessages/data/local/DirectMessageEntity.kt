package com.interlinedlist.android.feature.directmessages.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A locally cached direct message — the single source of truth for a thread.
 *
 * [conversationUsername] is the *other* participant's username, so all messages
 * of a conversation can be queried regardless of send direction. [createdAtMillis]
 * is the parsed epoch of [createdAt] for stable ordering; [createdAt] keeps the
 * raw ISO string for round-tripping to the API's `after` cursor.
 */
@Entity(tableName = "dm_message")
data class DirectMessageEntity(
    @PrimaryKey val id: String,
    val conversationUsername: String,
    val senderId: String,
    val recipientId: String,
    val body: String,
    val imageUrls: List<String>,
    val createdAt: String,
    val createdAtMillis: Long,
    val readAt: String?,
    val trashed: Boolean,
    /** True while an optimistic local send has not yet been confirmed by the server. */
    val pending: Boolean = false,
)
