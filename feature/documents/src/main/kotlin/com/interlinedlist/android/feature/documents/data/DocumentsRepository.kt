package com.interlinedlist.android.feature.documents.data

import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.documents.domain.Document
import com.interlinedlist.android.feature.documents.domain.DocumentFolder
import com.interlinedlist.android.feature.documents.domain.DocumentTemplate
import com.interlinedlist.android.feature.documents.domain.FolderContents
import com.interlinedlist.android.feature.documents.domain.FolderSummary
import com.interlinedlist.android.feature.documents.domain.ShareLink
import com.interlinedlist.android.feature.documents.domain.ShareRole
import com.interlinedlist.android.feature.documents.domain.SharedDocument
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first access to the document folder tree. The browser observes a
 * folder's [FolderContents] (subfolders + documents + breadcrumb) as a [Flow]
 * derived from Room — the source of truth — while [refreshTree] pulls the whole
 * tree from the API (`/folders` nests everything, `/documents` supplies unfiled
 * root docs) and writes it through the cache. Mutations write to the API and
 * update the cache so observers react immediately.
 */
interface DocumentsRepository {

    /**
     * Reactive contents of the folder identified by [folderId] (null / the root id
     * resolves to the top-level "Documents" node). Rebuilds from Room on any change.
     */
    fun observeFolderContents(folderId: String?): Flow<FolderContents>

    /** All folders flattened to summaries — used by the "move document" picker. */
    fun observeFolderSummaries(): Flow<List<FolderSummary>>

    /** A single cached document (null until first loaded). */
    fun observeDocument(id: String): Flow<Document?>

    /** Refreshes the entire folder tree + root documents from the API into Room. */
    suspend fun refreshTree(): ApiResult<Unit>

    /** Fetches a document detail (with body) and caches it. */
    suspend fun refreshDocument(id: String): ApiResult<Document>

    // --- Document mutations ------------------------------------------------

    /** Creates a document, optionally inside [folderId] (null == root). */
    suspend fun createDocument(
        title: String,
        content: String,
        isPublic: Boolean,
        folderId: String?,
    ): ApiResult<Document>

    suspend fun updateDocument(
        id: String,
        title: String,
        content: String,
        isPublic: Boolean,
        folderId: String?,
    ): ApiResult<Document>

    /** Moves a document into [folderId] (null == root/unfiled). */
    suspend fun moveDocument(id: String, folderId: String?): ApiResult<Unit>

    suspend fun deleteDocument(id: String): ApiResult<Unit>

    suspend fun uploadImage(
        documentId: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
    ): ApiResult<Unit>

    // --- Folder mutations --------------------------------------------------

    suspend fun createFolder(name: String, parentId: String?): ApiResult<DocumentFolder>

    suspend fun renameFolder(id: String, name: String): ApiResult<DocumentFolder>

    suspend fun moveFolder(id: String, newParentId: String?): ApiResult<DocumentFolder>

    /** Deletes a folder (server cascades to children + docs); prunes the cache. */
    suspend fun deleteFolder(id: String): ApiResult<Unit>

    // --- Templates & search ------------------------------------------------

    suspend fun getTemplates(): ApiResult<List<DocumentTemplate>>

    suspend fun createFromTemplate(
        templateId: String,
        targetFolderId: String?,
    ): ApiResult<Document>

    /** One-shot search against the API (not cached). */
    suspend fun searchDocuments(query: String): ApiResult<List<Document>>

    // --- Sharing -----------------------------------------------------------

    /** Existing public share links for a document. */
    suspend fun getShareLinks(documentId: String): ApiResult<List<ShareLink>>

    /** Creates a share link granting [role]; returns the created link. */
    suspend fun createShareLink(documentId: String, role: ShareRole): ApiResult<ShareLink>

    /** Revokes a share link by its token. */
    suspend fun revokeShareLink(documentId: String, token: String): ApiResult<Unit>

    /** Resolves a public `documents/shared/{token}` link to a read-only preview. */
    suspend fun resolveSharedDocument(token: String): ApiResult<SharedDocument>

    /** Claims edit/admin access to a shared document via its token. */
    suspend fun claimSharedDocument(token: String): ApiResult<Unit>
}
