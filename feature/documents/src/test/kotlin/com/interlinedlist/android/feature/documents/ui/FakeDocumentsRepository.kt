package com.interlinedlist.android.feature.documents.ui

import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.documents.data.DocumentsRepository
import com.interlinedlist.android.feature.documents.domain.Document
import com.interlinedlist.android.feature.documents.domain.DocumentFolder
import com.interlinedlist.android.feature.documents.domain.DocumentTemplate
import com.interlinedlist.android.feature.documents.domain.Pagination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [DocumentsRepository] for ViewModel tests. Backed by simple
 * StateFlows so tests can observe the same reactive behaviour as Room without a
 * device. Failure modes are injectable per operation.
 */
class FakeDocumentsRepository : DocumentsRepository {

    val rootDocuments = MutableStateFlow<List<Document>>(emptyList())
    val folderDocuments = MutableStateFlow<List<Document>>(emptyList())
    val foldersFlow = MutableStateFlow<List<DocumentFolder>>(emptyList())
    val documentFlow = MutableStateFlow<Document?>(null)

    var refreshResult: ApiResult<Pagination> = ApiResult.Success(Pagination.single(0))
    var loadMoreResult: ApiResult<Pagination> = ApiResult.Success(Pagination.single(0))
    var refreshDocumentResult: ApiResult<Document>? = null
    var createResult: ApiResult<Document>? = null
    var updateResult: ApiResult<Document>? = null
    var deleteResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var templatesResult: ApiResult<List<DocumentTemplate>> = ApiResult.Success(emptyList())
    var fromTemplateResult: ApiResult<Document>? = null
    var createFolderResult: ApiResult<DocumentFolder>? = null
    var searchResult: ApiResult<List<Document>> = ApiResult.Success(emptyList())

    var refreshCount = 0
    var loadMoreCount = 0
    var lastSelectedFolderId: String? = null
    var lastCreateTitle: String? = null
    var lastUpdate: Update? = null

    data class Update(val id: String, val title: String, val content: String, val isPublic: Boolean, val folderId: String?)

    override fun observeDocuments(folderId: String?) =
        if (folderId == null) rootDocuments.map { it } else folderDocuments.map { it }

    override fun observeDocument(id: String) = documentFlow.map { it }

    override fun observeFolders() = foldersFlow.map { it }

    override suspend fun refreshDocuments(folderId: String?): ApiResult<Pagination> {
        refreshCount++
        lastSelectedFolderId = folderId
        return refreshResult
    }

    override suspend fun loadMore(folderId: String?, pagination: Pagination): ApiResult<Pagination> {
        loadMoreCount++
        return loadMoreResult
    }

    override suspend fun refreshDocument(id: String): ApiResult<Document> =
        refreshDocumentResult ?: ApiResult.Failure(AppError.NotFound("not set"))

    override suspend fun createDocument(title: String, content: String, isPublic: Boolean): ApiResult<Document> {
        lastCreateTitle = title
        return createResult ?: ApiResult.Failure(AppError.Unknown("not set"))
    }

    override suspend fun updateDocument(
        id: String,
        title: String,
        content: String,
        isPublic: Boolean,
        folderId: String?,
    ): ApiResult<Document> {
        lastUpdate = Update(id, title, content, isPublic, folderId)
        return updateResult ?: ApiResult.Failure(AppError.Unknown("not set"))
    }

    override suspend fun deleteDocument(id: String): ApiResult<Unit> = deleteResult

    override suspend fun refreshFolders(): ApiResult<List<DocumentFolder>> =
        ApiResult.Success(foldersFlow.value)

    override suspend fun createFolder(name: String, parentId: String?): ApiResult<DocumentFolder> =
        createFolderResult ?: ApiResult.Failure(AppError.Unknown("not set"))

    override suspend fun getTemplates(): ApiResult<List<DocumentTemplate>> = templatesResult

    override suspend fun createFromTemplate(templateId: String, targetFolderId: String?): ApiResult<Document> =
        fromTemplateResult ?: ApiResult.Failure(AppError.Unknown("not set"))

    override suspend fun searchDocuments(query: String): ApiResult<List<Document>> = searchResult
}

/** Shorthand for building a domain document in tests. */
fun testDocument(
    id: String,
    title: String = "Doc $id",
    content: String? = null,
    folderId: String? = null,
) = Document(
    id = id,
    title = title,
    content = content,
    snippet = content?.take(20) ?: "",
    folderId = folderId,
    folderName = null,
    isPublic = false,
    updatedAt = null,
)
