package com.interlinedlist.android.feature.directmessages.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Access to cached direct messages. An interface so unit tests can back it with
 * an in-memory fake, keeping repository tests on the plain JVM.
 */
@Dao
interface DirectMessageDao {

    /** Oldest-first stream of a single conversation's non-trashed messages. */
    @Query(
        "SELECT * FROM dm_message " +
            "WHERE conversationUsername = :username AND trashed = 0 " +
            "ORDER BY createdAtMillis ASC",
    )
    fun observeThread(username: String): Flow<List<DirectMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(messages: List<DirectMessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: DirectMessageEntity)

    @Query("UPDATE dm_message SET readAt = :readAt WHERE id = :id")
    suspend fun setReadAt(id: String, readAt: String)

    @Query("UPDATE dm_message SET trashed = :trashed WHERE id = :id")
    suspend fun setTrashed(id: String, trashed: Boolean)

    @Query("DELETE FROM dm_message WHERE id = :id")
    suspend fun deleteById(id: String)

    /** The raw ISO timestamp of the newest cached message in a conversation. */
    @Query(
        "SELECT createdAt FROM dm_message " +
            "WHERE conversationUsername = :username " +
            "ORDER BY createdAtMillis DESC LIMIT 1",
    )
    suspend fun latestCreatedAt(username: String): String?

    /** Ids of every cached message in a conversation, for merge de-duplication. */
    @Query("SELECT id FROM dm_message WHERE conversationUsername = :username")
    suspend fun existingIds(username: String): List<String>

    @Query("DELETE FROM dm_message")
    suspend fun clear()
}
