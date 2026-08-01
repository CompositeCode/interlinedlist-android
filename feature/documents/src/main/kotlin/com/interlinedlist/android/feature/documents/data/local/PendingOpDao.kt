package com.interlinedlist.android.feature.documents.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingOpDao {

    /** Emits the count of queued ops so the UI can show an "unsynced" hint. */
    @Query("SELECT COUNT(*) FROM pending_op")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM pending_op ORDER BY queuedAt ASC")
    suspend fun all(): List<PendingOpEntity>

    /** Coalesces onto the same document id — the newest edit replaces any earlier one. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(op: PendingOpEntity)

    @Query("DELETE FROM pending_op WHERE documentId = :documentId")
    suspend fun deleteById(documentId: String)

    @Query("DELETE FROM pending_op WHERE documentId IN (:documentIds)")
    suspend fun deleteAllByIds(documentIds: List<String>)

    @Query("DELETE FROM pending_op")
    suspend fun clear()
}
