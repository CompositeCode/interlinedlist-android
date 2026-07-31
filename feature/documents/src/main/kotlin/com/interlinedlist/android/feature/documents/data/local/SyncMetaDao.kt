package com.interlinedlist.android.feature.documents.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SyncMetaDao {

    @Query("SELECT value FROM sync_meta WHERE key = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(row: SyncMetaEntity)

    @Query("DELETE FROM sync_meta WHERE key = :key")
    suspend fun clear(key: String)
}
