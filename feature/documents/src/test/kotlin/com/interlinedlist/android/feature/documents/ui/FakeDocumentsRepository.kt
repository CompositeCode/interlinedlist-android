package com.interlinedlist.android.feature.documents.ui

import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.documents.data.DocumentsRepository
import com.interlinedlist.android.feature.documents.domain.Document
import com.interlinedlist.android.feature.documents.domain.DocumentFolder
import com.interlinedlist.android.feature.documents.domain.DocumentTemplate
import com.interlinedlist.android.feature.documents.domain.FolderContents
import com.interlinedlist.android.feature.documents.domain.FolderNode
import com.interlinedlist.android.feature.documents.domain.FolderSummary
import com.interlinedlist.android.feature.documents.domain.FolderTree
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [DocumentsRepository] for ViewModel tests. Backed by StateFlows of a
 * flat folder list + documents so tests observe the same reactive tree-building
 * behaviour as Room without a device. Failure modes are injectable per operation.
 */
class FakeDocumentsRepository : DocumentsRepository {

    val folders = MutableStateFlow<List<DocumentFolder>>(emptyList())
    val documents = MutableStateFlow<List<Document>>(emptyList())
    val documentFlow = MutableStateFlow<Document?>(null)

    var refreshTreeResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var refreshDocumentResult: ApiResult<Document>? = null
    var createResult: ApiResult<Document>? = null
    var updateResult: ApiResult<Document>? = null
    var moveDocumentResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var deleteResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var uploadResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var createFolderResult: ApiResult<DocumentFolder>? = null
    var renameFolderResult: ApiResult<DocumentFolder>? = null
    var moveFolderResult: ApiResult<DocumentFolder>? = null
    var deleteFolderResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var templatesResult: ApiResult<List<DocumentTemplate>> = ApiResult.Success(emptyList())
    var fromTemplateResult: ApiResult<Document>? = null
    var searchResult: ApiResult<List<Document>> = ApiResult.Success(emptyList())

    var refreshTreeCount = 0
    var lastCreate: Create? = null
    var lastUpdate: Update? = null
    var lastMove: Move? = null
    var lastDeletedDocId: String? = null
    var lastFolderCreate: FolderCreate? = null
    var lastFolderRename: FolderRename? = null
    var lastDeletedFolderId: String? = null
    var lastSearchQuery: String? = null

    data class Create(val title: String, val content: String, val isPublic: Boolean, val folderId: String?)
    data class Update(val id: String, val title: String, val content: String, val isPublic: Boolean, val folderId: String?)
    data class Move(val id: String, val folderId: String?)
    data class FolderCreate(val name: String, val parentId: String?)
    data class FolderRename(val id: String, val name: String)

    private fun tree(): FolderNode {
        val byFolder = documents.value.filter { it.folderId != null }.groupBy { it.folderId!! }
        val root = documents.value.filter { it.folderId == null }
        return FolderTree.build(folders.value, byFolder, root)
    }

    override fun observeFolderContents(folderId: String?) =
        combine2(folders, documents) { _, _ -> FolderTree.contentsOf(tree(), folderId) }

    override fun observeFolderSummaries() =
        combine2(folders, documents) { _, _ -> flatten(tree()) }

    override fun observeDocument(id: String) = documentFlow.map { it }

    override suspend fun refreshTree(): ApiResult<Unit> {
        refreshTreeCount++
        return refreshTreeResult
    }

    override suspend fun refreshDocument(id: String): ApiResult<Document> =
        refreshDocumentResult ?: ApiResult.Failure(AppError.NotFound("not set"))

    override suspend fun createDocument(
        title: String,
        content: String,
        isPublic: Boolean,
        folderId: String?,
    ): ApiResult<Document> {
        lastCreate = Create(title, content, isPublic, folderId)
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

    override suspend fun moveDocument(id: String, folderId: String?): ApiResult<Unit> {
        lastMove = Move(id, folderId)
        return moveDocumentResult
    }

    override suspend fun deleteDocument(id: String): ApiResult<Unit> {
        lastDeletedDocId = id
        return deleteResult
    }

    override suspend fun uploadImage(
        documentId: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
    ): ApiResult<Unit> = uploadResult

    override suspend fun createFolder(name: String, parentId: String?): ApiResult<DocumentFolder> {
        lastFolderCreate = FolderCreate(name, parentId)
        return createFolderResult ?: ApiResult.Success(DocumentFolder("new-folder", name, parentId))
    }

    override suspend fun renameFolder(id: String, name: String): ApiResult<DocumentFolder> {
        lastFolderRename = FolderRename(id, name)
        return renameFolderResult ?: ApiResult.Success(DocumentFolder(id, name, null))
    }

    override suspend fun moveFolder(id: String, newParentId: String?): ApiResult<DocumentFolder> =
        moveFolderResult ?: ApiResult.Success(DocumentFolder(id, "moved", newParentId))

    override suspend fun deleteFolder(id: String): ApiResult<Unit> {
        lastDeletedFolderId = id
        return deleteFolderResult
    }

    override suspend fun getTemplates(): ApiResult<List<DocumentTemplate>> = templatesResult

    override suspend fun createFromTemplate(templateId: String, targetFolderId: String?): ApiResult<Document> =
        fromTemplateResult ?: ApiResult.Failure(AppError.Unknown("not set"))

    override suspend fun searchDocuments(query: String): ApiResult<List<Document>> {
        lastSearchQuery = query
        return searchResult
    }

    private fun flatten(node: FolderNode): List<FolderSummary> = buildList {
        node.children.forEach { child ->
            add(FolderSummary(child.id, child.name, child.documents.size, child.children.size))
            addAll(flatten(child))
        }
    }
}

/** Small combine helper to keep the fake independent of kotlinx.coroutines.flow.combine imports. */
private fun <A, B, R> combine2(
    a: MutableStateFlow<A>,
    b: MutableStateFlow<B>,
    transform: (A, B) -> R,
) = kotlinx.coroutines.flow.combine(a, b) { av, bv -> transform(av, bv) }

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
