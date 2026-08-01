package com.interlinedlist.android.feature.documents.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    /** Emits every cached document. The browser groups these by folder to build the tree. */
    @Query("SELECT * FROM document ORDER BY sortOrder ASC")
    fun observeAllDocuments(): Flow<List<DocumentEntity>>

    /** Emits root-level documents (no folder), ordered by their server sequence. */
    @Query("SELECT * FROM document WHERE folderId IS NULL ORDER BY sortOrder ASC")
    fun observeRootDocuments(): Flow<List<DocumentEntity>>

    /** Emits documents in a given folder, ordered by their server sequence. */
    @Query("SELECT * FROM document WHERE folderId = :folderId ORDER BY sortOrder ASC")
    fun observeDocumentsInFolder(folderId: String): Flow<List<DocumentEntity>>

    /** Emits a single document (or null) and re-emits on every change. */
    @Query("SELECT * FROM document WHERE id = :id")
    fun observeDocument(id: String): Flow<DocumentEntity?>

    @Query("SELECT * FROM document WHERE id = :id")
    suspend fun getDocument(id: String): DocumentEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM document")
    suspend fun maxSortOrder(): Int

    @Upsert
    suspend fun upsertAll(documents: List<DocumentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(document: DocumentEntity)

    @Query("DELETE FROM document WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Clears the root listing before a full refresh so removals propagate. */
    @Query("DELETE FROM document WHERE folderId IS NULL")
    suspend fun clearRoot()

    @Query("DELETE FROM document WHERE folderId = :folderId")
    suspend fun clearFolder(folderId: String)

    /** Wipes all documents before a full-tree refresh from `/folders` + `/documents`. */
    @Query("DELETE FROM document")
    suspend fun clearAll()
}
