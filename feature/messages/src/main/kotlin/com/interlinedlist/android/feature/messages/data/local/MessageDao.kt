package com.interlinedlist.android.feature.messages.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    /** Top-level feed messages in server order; re-emits on every change. */
    @Query("SELECT * FROM message WHERE parentId IS NULL ORDER BY feedOrder ASC")
    fun observeFeed(): Flow<List<MessageEntity>>

    /** Direct replies to a message in server order. */
    @Query("SELECT * FROM message WHERE parentId = :parentId ORDER BY feedOrder ASC")
    fun observeReplies(parentId: String): Flow<List<MessageEntity>>

    /** A single cached message (or null), re-emitting on change. */
    @Query("SELECT * FROM message WHERE id = :id")
    fun observeMessage(id: String): Flow<MessageEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)

    @Upsert
    suspend fun upsert(message: MessageEntity)

    @Query("DELETE FROM message WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Clears the top-level feed (used before writing a fresh refresh page). */
    @Query("DELETE FROM message WHERE parentId IS NULL")
    suspend fun clearFeed()

    /** Largest feed-order position currently stored (for append/load-more). */
    @Query("SELECT MAX(feedOrder) FROM message WHERE parentId IS NULL")
    suspend fun maxFeedOrder(): Long?
}
