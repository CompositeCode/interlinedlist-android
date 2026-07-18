package com.interlinedlist.android.feature.lists.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ListDao {

    /** Emits all cached lists, newest-updated first, re-emitting on every change. */
    @Query("SELECT * FROM cached_list ORDER BY updatedAt DESC")
    fun observeLists(): Flow<List<CachedListEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(lists: List<CachedListEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(list: CachedListEntity)

    @Query("DELETE FROM cached_list WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM cached_list")
    suspend fun clear()

    /**
     * Replaces the whole cache with [lists] in one transaction — used when a full
     * first page is fetched so removals on the server are reflected locally.
     */
    @androidx.room.Transaction
    suspend fun replaceAll(lists: List<CachedListEntity>) {
        clear()
        upsertAll(lists)
    }
}
