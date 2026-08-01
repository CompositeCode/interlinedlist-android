package com.interlinedlist.android.feature.documents.data

import com.interlinedlist.android.feature.documents.data.local.DocumentDao
import com.interlinedlist.android.feature.documents.data.local.DocumentEntity
import com.interlinedlist.android.feature.documents.data.local.FolderDao
import com.interlinedlist.android.feature.documents.data.local.FolderEntity
import com.interlinedlist.android.feature.documents.data.local.PendingOpDao
import com.interlinedlist.android.feature.documents.data.local.PendingOpEntity
import com.interlinedlist.android.feature.documents.data.local.SyncMetaDao
import com.interlinedlist.android.feature.documents.data.local.SyncMetaEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [DocumentDao] mirroring the real DAO's query semantics, so repository
 * tests can assert cache writes without Room/Robolectric.
 */
class FakeDocumentDao : DocumentDao {
    private val rows = MutableStateFlow<List<DocumentEntity>>(emptyList())

    fun snapshot(): List<DocumentEntity> = rows.value.sortedBy { it.sortOrder }

    override fun observeAllDocuments(): Flow<List<DocumentEntity>> =
        rows.map { list -> list.sortedBy { it.sortOrder } }

    override fun observeRootDocuments(): Flow<List<DocumentEntity>> =
        rows.map { list -> list.filter { it.folderId == null }.sortedBy { it.sortOrder } }

    override fun observeDocumentsInFolder(folderId: String): Flow<List<DocumentEntity>> =
        rows.map { list -> list.filter { it.folderId == folderId }.sortedBy { it.sortOrder } }

    override fun observeDocument(id: String): Flow<DocumentEntity?> =
        rows.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun getDocument(id: String): DocumentEntity? =
        rows.value.firstOrNull { it.id == id }

    override suspend fun maxSortOrder(): Int = rows.value.maxOfOrNull { it.sortOrder } ?: -1

    override suspend fun upsertAll(documents: List<DocumentEntity>) {
        documents.forEach { upsert(it) }
    }

    override suspend fun upsert(document: DocumentEntity) {
        rows.value = rows.value.filterNot { it.id == document.id } + document
    }

    override suspend fun deleteById(id: String) {
        rows.value = rows.value.filterNot { it.id == id }
    }

    override suspend fun clearRoot() {
        rows.value = rows.value.filterNot { it.folderId == null }
    }

    override suspend fun clearFolder(folderId: String) {
        rows.value = rows.value.filterNot { it.folderId == folderId }
    }

    override suspend fun clearAll() {
        rows.value = emptyList()
    }
}

class FakeFolderDao : FolderDao {
    private val rows = MutableStateFlow<List<FolderEntity>>(emptyList())

    fun snapshot(): List<FolderEntity> = rows.value.sortedBy { it.sortOrder }

    override fun observeFolders(): Flow<List<FolderEntity>> =
        rows.map { list -> list.sortedBy { it.sortOrder } }

    override suspend fun getFolder(id: String): FolderEntity? =
        rows.value.firstOrNull { it.id == id }

    override suspend fun maxSortOrder(): Int = rows.value.maxOfOrNull { it.sortOrder } ?: -1

    override suspend fun upsertAll(folders: List<FolderEntity>) {
        folders.forEach { upsert(it) }
    }

    override suspend fun upsert(folder: FolderEntity) {
        rows.value = rows.value.filterNot { it.id == folder.id } + folder
    }

    override suspend fun deleteById(id: String) {
        rows.value = rows.value.filterNot { it.id == id }
    }

    override suspend fun clear() {
        rows.value = emptyList()
    }
}

/** In-memory [PendingOpDao] mirroring the coalesce-by-documentId queue semantics. */
class FakePendingOpDao : PendingOpDao {
    private val rows = MutableStateFlow<List<PendingOpEntity>>(emptyList())

    fun snapshot(): List<PendingOpEntity> = rows.value.sortedBy { it.queuedAt }

    override fun observeCount(): Flow<Int> = rows.map { it.size }

    override suspend fun all(): List<PendingOpEntity> = rows.value.sortedBy { it.queuedAt }

    override suspend fun upsert(op: PendingOpEntity) {
        rows.value = rows.value.filterNot { it.documentId == op.documentId } + op
    }

    override suspend fun deleteById(documentId: String) {
        rows.value = rows.value.filterNot { it.documentId == documentId }
    }

    override suspend fun deleteAllByIds(documentIds: List<String>) {
        rows.value = rows.value.filterNot { it.documentId in documentIds }
    }

    override suspend fun clear() {
        rows.value = emptyList()
    }
}

/** In-memory [SyncMetaDao] for the delta-sync cursor. */
class FakeSyncMetaDao : SyncMetaDao {
    private val rows = mutableMapOf<String, String?>()

    override suspend fun get(key: String): String? = rows[key]

    override suspend fun put(row: SyncMetaEntity) {
        rows[row.key] = row.value
    }

    override suspend fun clear(key: String) {
        rows.remove(key)
    }
}
