package com.interlinedlist.android.feature.documents.data

import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.documents.domain.Document
import com.interlinedlist.android.feature.documents.domain.DocumentFolder
import com.interlinedlist.android.feature.documents.domain.DocumentTemplate
import com.interlinedlist.android.feature.documents.domain.Pagination
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first access to documents and folders. List/detail reads are served as
 * [Flow]s from Room (the source of truth); [refreshDocuments]/[loadMore] pull from
 * the API and upsert into the cache. Mutations write through to the API and update
 * the cache so the observing UI reflects the change immediately.
 */
interface DocumentsRepository {

    /** Root-level documents (no folder), or a folder's contents when [folderId] is set. */
    fun observeDocuments(folderId: String?): Flow<List<Document>>

    /** A single cached document (null until first loaded). */
    fun observeDocument(id: String): Flow<Document?>

    /** All cached folders. */
    fun observeFolders(): Flow<List<DocumentFolder>>

    /**
     * Fetches the first page for [folderId] from the API and replaces the cached
     * listing for that scope. Returns paging metadata for load-more.
     */
    suspend fun refreshDocuments(folderId: String?): ApiResult<Pagination>

    /** Appends the next page for [folderId] into the cache. */
    suspend fun loadMore(folderId: String?, pagination: Pagination): ApiResult<Pagination>

    /** Fetches a document detail (with body) and caches it. */
    suspend fun refreshDocument(id: String): ApiResult<Document>

    suspend fun createDocument(
        title: String,
        content: String,
        isPublic: Boolean,
    ): ApiResult<Document>

    suspend fun updateDocument(
        id: String,
        title: String,
        content: String,
        isPublic: Boolean,
        folderId: String?,
    ): ApiResult<Document>

    suspend fun deleteDocument(id: String): ApiResult<Unit>

    suspend fun refreshFolders(): ApiResult<List<DocumentFolder>>

    suspend fun createFolder(name: String, parentId: String?): ApiResult<DocumentFolder>

    suspend fun getTemplates(): ApiResult<List<DocumentTemplate>>

    suspend fun createFromTemplate(
        templateId: String,
        targetFolderId: String?,
    ): ApiResult<Document>

    /** One-shot search against the API (not cached). */
    suspend fun searchDocuments(query: String): ApiResult<List<Document>>
}
