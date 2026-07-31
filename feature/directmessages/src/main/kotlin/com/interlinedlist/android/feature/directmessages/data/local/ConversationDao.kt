package com.interlinedlist.android.feature.directmessages.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Access to cached conversation summaries. Declared as an interface so tests can
 * substitute a fast in-memory fake without pulling in the Android/Room runtime.
 */
@Dao
interface ConversationDao {

    /** Newest-first stream of conversations for the inbox; re-emits on change. */
    @Query("SELECT * FROM dm_conversation ORDER BY lastMessageAtMillis DESC")
    fun observeConversations(): Flow<List<ConversationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(conversations: List<ConversationEntity>)

    @Query("UPDATE dm_conversation SET hasUnread = 0 WHERE username = :username")
    suspend fun clearUnread(username: String)

    @Query("DELETE FROM dm_conversation")
    suspend fun clear()
}
