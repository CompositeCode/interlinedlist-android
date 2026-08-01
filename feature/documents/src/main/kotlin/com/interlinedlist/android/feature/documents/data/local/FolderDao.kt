package com.interlinedlist.android.feature.documents.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {

    @Query("SELECT * FROM folder ORDER BY sortOrder ASC")
    fun observeFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folder WHERE id = :id")
    suspend fun getFolder(id: String): FolderEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM folder")
    suspend fun maxSortOrder(): Int

    @Upsert
    suspend fun upsertAll(folders: List<FolderEntity>)

    @Upsert
    suspend fun upsert(folder: FolderEntity)

    @Query("DELETE FROM folder WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM folder")
    suspend fun clear()
}
