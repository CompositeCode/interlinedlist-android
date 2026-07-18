package com.interlinedlist.android.feature.documents.data

import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.core.common.result.map
import com.interlinedlist.android.core.network.error.safeApiCall
import com.interlinedlist.android.feature.documents.data.local.DocumentDao
import com.interlinedlist.android.feature.documents.data.local.FolderDao
import com.interlinedlist.android.feature.documents.data.local.toDomain
import com.interlinedlist.android.feature.documents.data.local.toEntity
import com.interlinedlist.android.feature.documents.data.mapper.toDomain
import com.interlinedlist.android.feature.documents.data.mapper.toPaginationDomain
import com.interlinedlist.android.feature.documents.data.mapper.toTemplate
import com.interlinedlist.android.feature.documents.data.remote.DocumentsApi
import com.interlinedlist.android.feature.documents.data.remote.dto.CreateDocumentRequest
import com.interlinedlist.android.feature.documents.data.remote.dto.CreateFolderRequest
import com.interlinedlist.android.feature.documents.data.remote.dto.DocumentListResponse
import com.interlinedlist.android.feature.documents.data.remote.dto.FromTemplateRequest
import com.interlinedlist.android.feature.documents.data.remote.dto.UpdateDocumentRequest
import com.interlinedlist.android.feature.documents.domain.Document
import com.interlinedlist.android.feature.documents.domain.DocumentFolder
import com.interlinedlist.android.feature.documents.domain.DocumentTemplate
import com.interlinedlist.android.feature.documents.domain.Pagination
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Room-backed, offline-first implementation. Reads observe Room; refreshes and
 * mutations call the API and write through to Room so the UI updates reactively.
 */
class DefaultDocumentsRepository @Inject constructor(
    private val api: DocumentsApi,
    private val documentDao: DocumentDao,
    private val folderDao: FolderDao,
    private val json: Json,
    private val dispatchers: DispatcherProvider,
) : DocumentsRepository {

    override fun observeDocuments(folderId: String?): Flow<List<Document>> {
        val source = if (folderId == null) {
            documentDao.observeRootDocuments()
        } else {
            documentDao.observeDocumentsInFolder(folderId)
        }
        return source.map { rows -> rows.map { it.toDomain() } }
    }

    override fun observeDocument(id: String): Flow<Document?> =
        documentDao.observeDocument(id).map { it?.toDomain() }

    override fun observeFolders(): Flow<List<DocumentFolder>> =
        folderDao.observeFolders().map { rows -> rows.map { it.toDomain() } }

    override suspend fun refreshDocuments(folderId: String?): ApiResult<Pagination> =
        withContext(dispatchers.io) {
            val result = safeApiCall(json) {
                if (folderId == null) {
                    api.getDocuments(limit = Pagination.DEFAULT_LIMIT, offset = 0)
                } else {
                    api.getFolderDocuments(folderId, limit = Pagination.DEFAULT_LIMIT, offset = 0)
                }
            }
            when (result) {
                is ApiResult.Success -> {
                    // Replace the listing for this scope so server-side deletions drop out.
                    if (folderId == null) documentDao.clearRoot() else documentDao.clearFolder(folderId)
                    ApiResult.Success(cachePage(result.data, folderId, startOrder = 0))
                }
                is ApiResult.Failure -> result
            }
        }

    override suspend fun loadMore(
        folderId: String?,
        pagination: Pagination,
    ): ApiResult<Pagination> = withContext(dispatchers.io) {
        if (!pagination.hasMore) return@withContext ApiResult.Success(pagination)
        val nextOffset = pagination.nextOffset
        val result = safeApiCall(json) {
            if (folderId == null) {
                api.getDocuments(limit = pagination.limit, offset = nextOffset)
            } else {
                api.getFolderDocuments(folderId, limit = pagination.limit, offset = nextOffset)
            }
        }
        when (result) {
            is ApiResult.Success ->
                ApiResult.Success(cachePage(result.data, folderId, startOrder = documentDao.maxSortOrder() + 1))
            is ApiResult.Failure -> result
        }
    }

    override suspend fun refreshDocument(id: String): ApiResult<Document> =
        withContext(dispatchers.io) {
            when (val result = safeApiCall(json) { api.getDocument(id).documentOrSelf }) {
                is ApiResult.Success -> {
                    val dto = result.data
                        ?: return@withContext ApiResult.Failure(AppError.NotFound("Document not found"))
                    val domain = dto.toDomain()
                    documentDao.upsert(domain.toEntity(sortOrder = existingOrder(id)))
                    ApiResult.Success(domain)
                }
                is ApiResult.Failure -> result
            }
        }

    override suspend fun createDocument(
        title: String,
        content: String,
        isPublic: Boolean,
    ): ApiResult<Document> = withContext(dispatchers.io) {
        val result = safeApiCall(json) {
            api.createDocument(CreateDocumentRequest(title, content, isPublic)).documentOrSelf
        }
        when (result) {
            is ApiResult.Success -> {
                val dto = result.data
                    ?: return@withContext ApiResult.Failure(AppError.Unknown("Document create returned no body"))
                val domain = dto.toDomain()
                documentDao.upsert(domain.toEntity(sortOrder = documentDao.maxSortOrder() + 1))
                ApiResult.Success(domain)
            }
            is ApiResult.Failure -> result
        }
    }

    override suspend fun updateDocument(
        id: String,
        title: String,
        content: String,
        isPublic: Boolean,
        folderId: String?,
    ): ApiResult<Document> = withContext(dispatchers.io) {
        val result = safeApiCall(json) {
            api.updateDocument(
                id,
                UpdateDocumentRequest(title = title, content = content, isPublic = isPublic, folderId = folderId),
            ).documentOrSelf
        }
        when (result) {
            is ApiResult.Success -> {
                // Fall back to the locally-known values if the server echoes a thin body.
                val domain = result.data?.toDomain()?.let {
                    it.copy(content = it.content ?: content)
                } ?: Document(
                    id = id,
                    title = title,
                    content = content,
                    snippet = Document.snippetFrom(content),
                    folderId = folderId,
                    folderName = null,
                    isPublic = isPublic,
                    updatedAt = null,
                )
                documentDao.upsert(domain.toEntity(sortOrder = existingOrder(id)))
                ApiResult.Success(domain)
            }
            is ApiResult.Failure -> result
        }
    }

    override suspend fun deleteDocument(id: String): ApiResult<Unit> =
        withContext(dispatchers.io) {
            when (val result = safeApiCall(json) { api.deleteDocument(id) }) {
                is ApiResult.Success -> {
                    documentDao.deleteById(id)
                    ApiResult.Success(Unit)
                }
                is ApiResult.Failure -> result
            }
        }

    override suspend fun refreshFolders(): ApiResult<List<DocumentFolder>> =
        withContext(dispatchers.io) {
            when (val result = safeApiCall(json) { api.getFolders() }) {
                is ApiResult.Success -> {
                    val folders = result.data.foldersOrEmpty.map { it.toDomain() }
                    folderDao.clear()
                    folderDao.upsertAll(folders.mapIndexed { i, f -> f.toEntity(sortOrder = i) })
                    ApiResult.Success(folders)
                }
                is ApiResult.Failure -> result
            }
        }

    override suspend fun createFolder(name: String, parentId: String?): ApiResult<DocumentFolder> =
        withContext(dispatchers.io) {
            val result = safeApiCall(json) {
                api.createFolder(CreateFolderRequest(name, parentId)).folderOrSelf
            }
            when (result) {
                is ApiResult.Success -> {
                    val dto = result.data
                        ?: return@withContext ApiResult.Failure(AppError.Unknown("Folder create returned no body"))
                    val domain = dto.toDomain()
                    folderDao.upsert(domain.toEntity(sortOrder = Int.MAX_VALUE))
                    ApiResult.Success(domain)
                }
                is ApiResult.Failure -> result
            }
        }

    override suspend fun getTemplates(): ApiResult<List<DocumentTemplate>> =
        withContext(dispatchers.io) {
            safeApiCall(json) { api.getTemplates() }
                .map { response -> response.documentsOrEmpty.map { it.toTemplate() } }
        }

    override suspend fun createFromTemplate(
        templateId: String,
        targetFolderId: String?,
    ): ApiResult<Document> = withContext(dispatchers.io) {
        val result = safeApiCall(json) {
            api.createFromTemplate(FromTemplateRequest(templateId, targetFolderId)).documentOrSelf
        }
        when (result) {
            is ApiResult.Success -> {
                val dto = result.data
                    ?: return@withContext ApiResult.Failure(AppError.Unknown("Template create returned no body"))
                val domain = dto.toDomain()
                documentDao.upsert(domain.toEntity(sortOrder = documentDao.maxSortOrder() + 1))
                ApiResult.Success(domain)
            }
            is ApiResult.Failure -> result
        }
    }

    override suspend fun searchDocuments(query: String): ApiResult<List<Document>> =
        withContext(dispatchers.io) {
            safeApiCall(json) { api.searchDocuments(query) }
                .map { response -> response.documentsOrEmpty.map { it.toDomain() } }
        }

    /** Upserts a page of documents starting at [startOrder]; returns its paging metadata. */
    private suspend fun cachePage(
        response: DocumentListResponse,
        folderId: String?,
        startOrder: Int,
    ): Pagination {
        val documents = response.documentsOrEmpty.map { it.toDomain() }
        val entities = documents.mapIndexed { i, doc ->
            // Root refreshes clear the table, so folderId on a root doc is honoured as-is.
            doc.copy(folderId = doc.folderId ?: folderId)
                .toEntity(sortOrder = startOrder + i)
        }
        documentDao.upsertAll(entities)
        return response.pagination.toPaginationDomain(fallbackCount = documents.size)
    }

    private suspend fun existingOrder(id: String): Int =
        documentDao.getDocument(id)?.sortOrder ?: (documentDao.maxSortOrder() + 1)
}
