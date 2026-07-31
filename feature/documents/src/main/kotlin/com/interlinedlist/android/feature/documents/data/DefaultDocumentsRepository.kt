package com.interlinedlist.android.feature.documents.data

import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.core.common.result.map
import com.interlinedlist.android.core.network.error.safeApiCall
import com.interlinedlist.android.feature.documents.data.local.DocumentDao
import com.interlinedlist.android.feature.documents.data.local.FolderDao
import com.interlinedlist.android.feature.documents.data.local.PendingOpDao
import com.interlinedlist.android.feature.documents.data.local.PendingOpEntity
import com.interlinedlist.android.feature.documents.data.local.SyncMetaDao
import com.interlinedlist.android.feature.documents.data.local.SyncMetaEntity
import com.interlinedlist.android.feature.documents.data.local.toDomain
import com.interlinedlist.android.feature.documents.data.local.toEntity
import com.interlinedlist.android.feature.documents.data.mapper.toCandidate
import com.interlinedlist.android.feature.documents.data.mapper.toDomain
import com.interlinedlist.android.feature.documents.data.mapper.toSharedDocument
import com.interlinedlist.android.feature.documents.data.mapper.toTemplate
import com.interlinedlist.android.feature.documents.data.remote.DocumentsApi
import com.interlinedlist.android.feature.documents.data.remote.dto.CreateDocumentRequest
import com.interlinedlist.android.feature.documents.data.remote.dto.CreateFolderRequest
import com.interlinedlist.android.feature.documents.data.remote.dto.CreateShareLinkRequest
import com.interlinedlist.android.feature.documents.data.remote.dto.FromTemplateRequest
import com.interlinedlist.android.feature.documents.data.remote.dto.InviteCollaboratorRequest
import com.interlinedlist.android.feature.documents.data.remote.dto.SyncOperationDto
import com.interlinedlist.android.feature.documents.data.remote.dto.SyncPushRequest
import com.interlinedlist.android.feature.documents.data.remote.dto.UpdateCollaboratorRoleRequest
import com.interlinedlist.android.feature.documents.data.remote.dto.UpdateDocumentRequest
import com.interlinedlist.android.feature.documents.data.remote.dto.UpdateFolderRequest
import com.interlinedlist.android.feature.documents.domain.Collaborator
import com.interlinedlist.android.feature.documents.domain.CollaboratorCandidate
import com.interlinedlist.android.feature.documents.domain.CollaboratorRole
import com.interlinedlist.android.feature.documents.domain.Document
import com.interlinedlist.android.feature.documents.domain.DocumentFolder
import com.interlinedlist.android.feature.documents.domain.DocumentTemplate
import com.interlinedlist.android.feature.documents.domain.FolderContents
import com.interlinedlist.android.feature.documents.domain.FolderNode
import com.interlinedlist.android.feature.documents.domain.FolderSummary
import com.interlinedlist.android.feature.documents.domain.FolderTree
import com.interlinedlist.android.feature.documents.domain.Presence
import com.interlinedlist.android.feature.documents.domain.ShareLink
import com.interlinedlist.android.feature.documents.domain.ShareRole
import com.interlinedlist.android.feature.documents.domain.SharedDocument
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Room-backed, offline-first implementation. The browser observes a folder's
 * contents built from ALL cached folders + documents (so drilling in/out never
 * hits the network); [refreshTree] pulls the whole tree once and writes it
 * through. Mutations call the API then patch the cache so observers react.
 */
class DefaultDocumentsRepository @Inject constructor(
    private val api: DocumentsApi,
    private val documentDao: DocumentDao,
    private val folderDao: FolderDao,
    private val pendingOpDao: PendingOpDao,
    private val syncMetaDao: SyncMetaDao,
    private val json: Json,
    private val dispatchers: DispatcherProvider,
) : DocumentsRepository {

    /** The current tree, recomputed whenever folders or documents change in Room. */
    private val treeFlow: Flow<FolderNode> = combine(
        folderDao.observeFolders(),
        documentDao.observeAllDocuments(),
    ) { folders, documents ->
        buildTree(folders.map { it.toDomain() }, documents.map { it.toDomain() })
    }

    override fun observeFolderContents(folderId: String?): Flow<FolderContents> =
        treeFlow.map { tree -> FolderTree.contentsOf(tree, folderId) }

    override fun observeFolderSummaries(): Flow<List<FolderSummary>> =
        treeFlow.map { tree -> flattenSummaries(tree) }

    override fun observeDocument(id: String): Flow<Document?> =
        documentDao.observeDocument(id).map { it?.toDomain() }

    override fun observePendingCount(): Flow<Int> = pendingOpDao.observeCount()

    override suspend fun refreshTree(): ApiResult<Unit> = withContext(dispatchers.io) {
        // One call returns the nested folder tree with embedded docs; a second returns
        // the unfiled root documents. We replace the whole cache so deletions drop out.
        val foldersResult = safeApiCall(json) { api.getFolders() }
        val folders = when (foldersResult) {
            is ApiResult.Success -> foldersResult.data.foldersOrEmpty
            is ApiResult.Failure -> return@withContext foldersResult
        }
        val rootResult = safeApiCall(json) { api.getRootDocuments() }
        val rootDocs = when (rootResult) {
            is ApiResult.Success -> rootResult.data.documentsOrEmpty
            is ApiResult.Failure -> return@withContext rootResult
        }

        folderDao.clear()
        documentDao.clearAll()

        folderDao.upsertAll(
            folders.mapIndexed { i, dto -> dto.toDomain().toEntity(sortOrder = i) },
        )

        var order = 0
        val docEntities = buildList {
            // Root/unfiled documents first, then each folder's embedded documents.
            rootDocs.forEach { dto ->
                add(dto.toDomain().copy(folderId = null).toEntity(sortOrder = order++))
            }
            folders.forEach { folder ->
                folder.documentsOrEmpty.forEach { dto ->
                    add(dto.toDomain().copy(folderId = folder.id).toEntity(sortOrder = order++))
                }
            }
        }
        documentDao.upsertAll(docEntities)
        ApiResult.Success(Unit)
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
        folderId: String?,
    ): ApiResult<Document> = withContext(dispatchers.io) {
        val result = safeApiCall(json) {
            api.createDocument(CreateDocumentRequest(title, content, isPublic)).documentOrSelf
        }
        when (result) {
            is ApiResult.Success -> {
                val dto = result.data
                    ?: return@withContext ApiResult.Failure(AppError.Unknown("Document create returned no body"))
                // The root create endpoint always yields an unfiled doc; assign it to the
                // requested folder if one was given so it lands in the right place.
                val domain = dto.toDomain().let { if (folderId != null) it.copy(folderId = folderId) else it }
                documentDao.upsert(domain.toEntity(sortOrder = documentDao.maxSortOrder() + 1))
                if (folderId != null) {
                    // Best-effort move so the server record matches the cache.
                    safeApiCall(json) { api.updateDocument(domain.id, UpdateDocumentRequest(folderId = folderId)) }
                }
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

    override suspend fun moveDocument(id: String, folderId: String?): ApiResult<Unit> =
        withContext(dispatchers.io) {
            val result = safeApiCall(json) {
                api.updateDocument(id, UpdateDocumentRequest(folderId = folderId))
            }
            when (result) {
                is ApiResult.Success -> {
                    // Patch the cached row's folder so the browser reflects the move offline.
                    documentDao.getDocument(id)?.let { cached ->
                        documentDao.upsert(cached.copy(folderId = folderId))
                    }
                    ApiResult.Success(Unit)
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

    override suspend fun uploadImage(
        documentId: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
    ): ApiResult<Unit> = withContext(dispatchers.io) {
        val part = MultipartBody.Part.createFormData(
            name = "image",
            filename = fileName,
            body = bytes.toRequestBody(mimeType.toMediaTypeOrNull()),
        )
        safeApiCall(json) { api.uploadImage(documentId, part) }.map { }
    }

    override suspend fun createFolder(name: String, parentId: String?): ApiResult<DocumentFolder> =
        withContext(dispatchers.io) {
            val result = safeApiCall(json) {
                api.createFolder(CreateFolderRequest(name, parentId?.realOrNull())).folderOrSelf
            }
            when (result) {
                is ApiResult.Success -> {
                    val dto = result.data
                        ?: return@withContext ApiResult.Failure(AppError.Unknown("Folder create returned no body"))
                    val domain = dto.toDomain().copy(parentId = parentId?.realOrNull())
                    folderDao.upsert(domain.toEntity(sortOrder = folderDao.maxSortOrder() + 1))
                    ApiResult.Success(domain)
                }
                is ApiResult.Failure -> result
            }
        }

    override suspend fun renameFolder(id: String, name: String): ApiResult<DocumentFolder> =
        withContext(dispatchers.io) {
            val result = safeApiCall(json) {
                api.updateFolder(id, UpdateFolderRequest(name = name)).folderOrSelf
            }
            when (result) {
                is ApiResult.Success -> {
                    val cached = folderDao.getFolder(id)
                    val dto = result.data
                    val domain = dto?.toDomain()?.copy(
                        parentId = dto.parentId ?: cached?.parentId,
                    ) ?: DocumentFolder(id = id, name = name, parentId = cached?.parentId)
                    folderDao.upsert(domain.toEntity(sortOrder = cached?.sortOrder ?: (folderDao.maxSortOrder() + 1)))
                    ApiResult.Success(domain)
                }
                is ApiResult.Failure -> result
            }
        }

    override suspend fun moveFolder(id: String, newParentId: String?): ApiResult<DocumentFolder> =
        withContext(dispatchers.io) {
            val result = safeApiCall(json) {
                api.updateFolder(id, UpdateFolderRequest(parentId = newParentId?.realOrNull())).folderOrSelf
            }
            when (result) {
                is ApiResult.Success -> {
                    val cached = folderDao.getFolder(id)
                    val domain = (result.data?.toDomain() ?: DocumentFolder(id, cached?.name ?: "Untitled folder", null))
                        .copy(parentId = newParentId?.realOrNull())
                    folderDao.upsert(domain.toEntity(sortOrder = cached?.sortOrder ?: (folderDao.maxSortOrder() + 1)))
                    ApiResult.Success(domain)
                }
                is ApiResult.Failure -> result
            }
        }

    override suspend fun deleteFolder(id: String): ApiResult<Unit> =
        withContext(dispatchers.io) {
            when (val result = safeApiCall(json) { api.deleteFolder(id) }) {
                is ApiResult.Success -> {
                    // Server cascades; mirror that locally so the tree updates offline.
                    pruneFolderCascade(id)
                    ApiResult.Success(Unit)
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
            api.createFromTemplate(FromTemplateRequest(templateId, targetFolderId?.realOrNull())).documentOrSelf
        }
        when (result) {
            is ApiResult.Success -> {
                val dto = result.data
                    ?: return@withContext ApiResult.Failure(AppError.Unknown("Template create returned no body"))
                val domain = dto.toDomain().let {
                    if (targetFolderId != null) it.copy(folderId = targetFolderId.realOrNull()) else it
                }
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

    override suspend fun getShareLinks(documentId: String): ApiResult<List<ShareLink>> =
        withContext(dispatchers.io) {
            safeApiCall(json) { api.getShareLinks(documentId) }
                .map { response -> response.items.map { it.toDomain() } }
        }

    override suspend fun createShareLink(documentId: String, role: ShareRole): ApiResult<ShareLink> =
        withContext(dispatchers.io) {
            when (val result = safeApiCall(json) {
                api.createShareLink(documentId, CreateShareLinkRequest(role = role.apiValue))
            }) {
                is ApiResult.Success -> {
                    val dto = result.data.linkOrSelf
                        ?: return@withContext ApiResult.Failure(
                            AppError.Unknown("Share link create returned no token"),
                        )
                    ApiResult.Success(dto.toDomain())
                }
                is ApiResult.Failure -> result
            }
        }

    override suspend fun revokeShareLink(documentId: String, token: String): ApiResult<Unit> =
        withContext(dispatchers.io) {
            safeApiCall(json) { api.revokeShareLink(documentId, token) }.map { }
        }

    override suspend fun resolveSharedDocument(token: String): ApiResult<SharedDocument> =
        withContext(dispatchers.io) {
            safeApiCall(json) { api.resolveSharedDocument(token) }
                .map { response -> response.toSharedDocument(token) }
        }

    override suspend fun claimSharedDocument(token: String): ApiResult<Unit> =
        withContext(dispatchers.io) {
            safeApiCall(json) { api.claimSharedDocument(token) }.map { }
        }

    // --- Versioned save (PATCH + If-Match) --------------------------------

    override suspend fun patchDocument(
        id: String,
        title: String,
        content: String,
        isPublic: Boolean,
        folderId: String?,
        expectedVersion: Int?,
    ): SaveOutcome = withContext(dispatchers.io) {
        val body = UpdateDocumentRequest(
            title = title,
            content = content,
            isPublic = isPublic,
            folderId = folderId,
        )
        val result = safeApiCall(json) {
            api.patchDocument(id, body, ifMatch = expectedVersion?.toString()).documentOrSelf
        }
        when (result) {
            is ApiResult.Success -> {
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
                    version = expectedVersion?.plus(1),
                )
                documentDao.upsert(domain.toEntity(sortOrder = existingOrder(id)))
                // A successful save supersedes anything queued for this doc.
                pendingOpDao.deleteById(id)
                SaveOutcome.Success(domain)
            }
            is ApiResult.Failure -> when (result.error) {
                // Version mismatch → let the UI reload/retry; do NOT clobber or queue.
                is AppError.Conflict -> SaveOutcome.Conflict(result.error.message)
                // Offline → persist locally to push on the next sync.
                is AppError.Network -> {
                    pendingOpDao.upsert(
                        PendingOpEntity(
                            documentId = id,
                            op = PendingOpEntity.OP_UPDATE,
                            title = title,
                            content = content,
                            isPublic = isPublic,
                            folderId = folderId,
                            version = expectedVersion,
                            queuedAt = System.currentTimeMillis(),
                        ),
                    )
                    SaveOutcome.Queued
                }
                else -> SaveOutcome.Error(result.error.message)
            }
        }
    }

    // --- Delta sync --------------------------------------------------------

    override suspend fun pullDelta(): ApiResult<Unit> = withContext(dispatchers.io) {
        val cursor = syncMetaDao.get(SyncMetaEntity.KEY_CURSOR)
        when (val result = safeApiCall(json) { api.pullSync(cursor) }) {
            is ApiResult.Success -> {
                reconcile(result.data.folders, result.data.documents)
                result.data.lastSyncAt?.let {
                    syncMetaDao.put(SyncMetaEntity(SyncMetaEntity.KEY_CURSOR, it))
                }
                ApiResult.Success(Unit)
            }
            is ApiResult.Failure -> result
        }
    }

    override suspend fun pushPendingOps(): ApiResult<Unit> = withContext(dispatchers.io) {
        val pending = pendingOpDao.all()
        if (pending.isEmpty()) return@withContext ApiResult.Success(Unit)

        val operations = pending.map { op ->
            SyncOperationDto(
                id = op.documentId,
                op = op.op,
                title = op.title,
                content = op.content,
                isPublic = op.isPublic,
                folderId = op.folderId,
                version = op.version,
            )
        }
        when (val result = safeApiCall(json) { api.pushSync(SyncPushRequest(operations)) }) {
            is ApiResult.Success -> {
                pendingOpDao.deleteAllByIds(pending.map { it.documentId })
                // Fold any server echo back into the cache and advance the cursor.
                reconcile(result.data.folders, result.data.documents)
                result.data.lastSyncAt?.let {
                    syncMetaDao.put(SyncMetaEntity(SyncMetaEntity.KEY_CURSOR, it))
                }
                ApiResult.Success(Unit)
            }
            is ApiResult.Failure -> result
        }
    }

    // --- Collaborators -----------------------------------------------------

    override suspend fun getCollaborators(documentId: String): ApiResult<List<Collaborator>> =
        withContext(dispatchers.io) {
            safeApiCall(json) { api.getCollaborators(documentId) }
                .map { response -> response.items.map { it.toDomain() } }
        }

    override suspend fun searchCollaboratorUsers(
        documentId: String,
        query: String,
    ): ApiResult<List<CollaboratorCandidate>> = withContext(dispatchers.io) {
        safeApiCall(json) {
            api.searchCollaboratorUsers(
                id = documentId,
                search = query.takeIf { it.isNotBlank() },
                excludeCollaborators = true,
            )
        }.map { response -> response.users.map { it.toCandidate() } }
    }

    override suspend fun inviteCollaborator(
        documentId: String,
        userId: String,
        role: CollaboratorRole,
    ): ApiResult<Collaborator> = withContext(dispatchers.io) {
        when (val result = safeApiCall(json) {
            api.inviteCollaborator(documentId, InviteCollaboratorRequest(userId, role.apiValue))
        }) {
            is ApiResult.Success -> {
                val dto = result.data.collaboratorOrNull
                val domain = dto?.toDomain()
                    ?: Collaborator(userId, role, null, null, null, null)
                ApiResult.Success(domain)
            }
            is ApiResult.Failure -> result
        }
    }

    override suspend fun updateCollaboratorRole(
        documentId: String,
        userId: String,
        role: CollaboratorRole,
    ): ApiResult<Unit> = withContext(dispatchers.io) {
        safeApiCall(json) {
            api.updateCollaboratorRole(documentId, userId, UpdateCollaboratorRoleRequest(role.apiValue))
        }.map { }
    }

    override suspend fun removeCollaborator(documentId: String, userId: String): ApiResult<Unit> =
        withContext(dispatchers.io) {
            safeApiCall(json) { api.removeCollaborator(documentId, userId) }.map { }
        }

    // --- Presence ----------------------------------------------------------

    override suspend fun sendPresence(documentId: String): ApiResult<List<Presence>> =
        withContext(dispatchers.io) {
            safeApiCall(json) { api.sendPresence(documentId) }
                .map { response -> response.items.map { it.toDomain() } }
        }

    override suspend fun leavePresence(documentId: String): ApiResult<Unit> =
        withContext(dispatchers.io) {
            safeApiCall(json) { api.leavePresence(documentId) }.map { }
        }

    // --- Helpers -----------------------------------------------------------

    /**
     * Reconciles a batch of flat, versioned sync rows into Room: tombstones drop the
     * cached row; upserts replace by id (append at the tail if new, keep the slot if
     * known). Server ordering within a delta is not authoritative, so we sort new
     * rows after existing ones.
     */
    private suspend fun reconcile(
        folders: List<com.interlinedlist.android.feature.documents.data.remote.dto.SyncFolderDto>,
        documents: List<com.interlinedlist.android.feature.documents.data.remote.dto.SyncDocumentDto>,
    ) {
        folders.forEach { dto ->
            if (dto.isDeleted) {
                pruneFolderCascade(dto.id)
            } else {
                val existing = folderDao.getFolder(dto.id)
                val order = existing?.sortOrder ?: (folderDao.maxSortOrder() + 1)
                folderDao.upsert(dto.toDomain().toEntity(sortOrder = order))
            }
        }
        documents.forEach { dto ->
            if (dto.isDeleted) {
                documentDao.deleteById(dto.id)
            } else {
                val existing = documentDao.getDocument(dto.id)
                val order = existing?.sortOrder ?: (documentDao.maxSortOrder() + 1)
                // Preserve a locally cached body if the delta row omits content.
                documentDao.upsert(
                    dto.toDomain().toEntity(sortOrder = order, existingContent = existing?.content),
                )
            }
        }
    }

    private fun buildTree(folders: List<DocumentFolder>, documents: List<Document>): FolderNode {
        val byFolder = documents.filter { it.folderId != null }.groupBy { it.folderId!! }
        val root = documents.filter { it.folderId == null }
        return FolderTree.build(folders, byFolder, root)
    }

    private fun flattenSummaries(node: FolderNode): List<FolderSummary> = buildList {
        node.children.forEach { child ->
            add(FolderSummary(child.id, child.name, child.documents.size, child.children.size))
            addAll(flattenSummaries(child))
        }
    }

    /** Recursively removes a folder, its descendants, and their documents from Room. */
    private suspend fun pruneFolderCascade(folderId: String) {
        val snapshot = folderDao.observeFolders().first()
        val toRemove = mutableListOf(folderId)
        var i = 0
        while (i < toRemove.size) {
            val current = toRemove[i]
            snapshot.filter { it.parentId == current }.forEach { toRemove.add(it.id) }
            i++
        }
        toRemove.forEach { id ->
            documentDao.clearFolder(id)
            folderDao.deleteById(id)
        }
    }

    private suspend fun existingOrder(id: String): Int =
        documentDao.getDocument(id)?.sortOrder ?: (documentDao.maxSortOrder() + 1)

    /** Treats the synthetic root id as "no parent" for API calls. */
    private fun String.realOrNull(): String? = takeUnless { it == FolderNode.ROOT_ID }
}
