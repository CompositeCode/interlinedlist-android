package com.interlinedlist.android.feature.documents.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {

    @Query("SELECT * FROM folder ORDER BY sortOrder ASC")
    fun observeFolders(): Flow<List<FolderEntity>>

    @Upsert
    suspend fun upsertAll(folders: List<FolderEntity>)

    @Upsert
    suspend fun upsert(folder: FolderEntity)

    @Query("DELETE FROM folder")
    suspend fun clear()
}
