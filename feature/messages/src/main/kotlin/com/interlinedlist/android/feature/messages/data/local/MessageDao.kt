package com.interlinedlist.android.feature.messages.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    /**
     * Top-level feed messages in server order; re-emits on every change.
     * Scheduled (not-yet-published) messages are excluded — they live in their
     * own view, not the public feed.
     */
    @Query("SELECT * FROM message WHERE parentId IS NULL AND scheduledAt IS NULL ORDER BY feedOrder ASC")
    fun observeFeed(): Flow<List<MessageEntity>>

    /** Direct replies to a message in server order. */
    @Query("SELECT * FROM message WHERE parentId = :parentId ORDER BY feedOrder ASC")
    fun observeReplies(parentId: String): Flow<List<MessageEntity>>

    /** A single cached message (or null), re-emitting on change. */
    @Query("SELECT * FROM message WHERE id = :id")
    fun observeMessage(id: String): Flow<MessageEntity?>

    /** Cached scheduled messages, soonest first; re-emits on every change. */
    @Query("SELECT * FROM message WHERE scheduledAt IS NOT NULL ORDER BY scheduledAt ASC")
    fun observeScheduled(): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)

    @Upsert
    suspend fun upsert(message: MessageEntity)

    @Query("DELETE FROM message WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Removes every cached message authored by [username] (used to hide a blocked
     * or muted author's messages from the local feed/replies immediately).
     */
    @Query("DELETE FROM message WHERE authorUsername = :username")
    suspend fun deleteByAuthorUsername(username: String)

    /** Clears the top-level feed (used before writing a fresh refresh page). */
    @Query("DELETE FROM message WHERE parentId IS NULL AND scheduledAt IS NULL")
    suspend fun clearFeed()

    /** Clears the cached scheduled messages (used before a fresh refresh). */
    @Query("DELETE FROM message WHERE scheduledAt IS NOT NULL")
    suspend fun clearScheduled()

    /** Largest feed-order position currently stored (for append/load-more). */
    @Query("SELECT MAX(feedOrder) FROM message WHERE parentId IS NULL AND scheduledAt IS NULL")
    suspend fun maxFeedOrder(): Long?
}
