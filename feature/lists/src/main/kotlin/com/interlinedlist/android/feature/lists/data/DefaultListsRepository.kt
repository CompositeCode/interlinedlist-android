package com.interlinedlist.android.feature.lists.data

import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.map
import com.interlinedlist.android.core.network.error.safeApiCall
import com.interlinedlist.android.feature.lists.data.local.ListDao
import com.interlinedlist.android.feature.lists.data.remote.ListsApi
import com.interlinedlist.android.feature.lists.data.remote.dto.AddWatcherRequest
import com.interlinedlist.android.feature.lists.data.remote.dto.CreateConnectionRequest
import com.interlinedlist.android.feature.lists.data.remote.dto.CreateFolderRequest
import com.interlinedlist.android.feature.lists.data.remote.dto.CreateListRequest
import com.interlinedlist.android.feature.lists.data.remote.dto.CreateShareLinkRequest
import com.interlinedlist.android.feature.lists.data.remote.dto.ListDto
import com.interlinedlist.android.feature.lists.data.remote.dto.RowDto
import com.interlinedlist.android.feature.lists.data.remote.dto.RowWriteRequest
import com.interlinedlist.android.feature.lists.data.remote.dto.UpdateSchemaRequest
import com.interlinedlist.android.feature.lists.data.remote.dto.UpdateWatcherRoleRequest
import com.interlinedlist.android.feature.lists.domain.ListConnection
import com.interlinedlist.android.feature.lists.domain.ListDetail
import com.interlinedlist.android.feature.lists.domain.ListFolder
import com.interlinedlist.android.feature.lists.domain.ListRow
import com.interlinedlist.android.feature.lists.domain.ListSchema
import com.interlinedlist.android.feature.lists.domain.ListSummary
import com.interlinedlist.android.feature.lists.domain.Paged
import com.interlinedlist.android.feature.lists.domain.RefreshResult
import com.interlinedlist.android.feature.lists.domain.ShareLink
import com.interlinedlist.android.feature.lists.domain.ShareRole
import com.interlinedlist.android.feature.lists.domain.SharedList
import com.interlinedlist.android.feature.lists.domain.SharedListResolution
import com.interlinedlist.android.feature.lists.domain.Watcher
import com.interlinedlist.android.feature.lists.domain.WatcherCandidate
import com.interlinedlist.android.feature.lists.domain.WatcherRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject

/**
 * Offline-first [ListsRepository]. The index is served from Room and refreshed
 * from the API (Room stays the source of truth); mutations write through to the
 * API and then update the cache. Failures are normalised to [ApiResult] via
 * [safeApiCall], which maps a subscription 403 to `AppError.SubscriptionRequired`.
 */
class DefaultListsRepository @Inject constructor(
    private val api: ListsApi,
    private val listDao: ListDao,
    private val json: kotlinx.serialization.json.Json,
    private val dispatchers: DispatcherProvider,
) : ListsRepository {

    override fun observeLists(): Flow<List<ListSummary>> =
        listDao.observeLists().map { entities -> entities.map(ListMapper::summaryFromEntity) }

    override suspend fun refreshLists(limit: Int): ApiResult<Paged<ListSummary>> =
        withContext(dispatchers.io) {
            when (val result = safeApiCall(json) { api.getLists(limit = limit, offset = 0) }) {
                is ApiResult.Success -> {
                    val summaries = result.data.items.map(ListMapper::summaryFromDto)
                    // First page → replace so server-side deletions are reflected.
                    listDao.replaceAll(summaries.map(ListMapper::summaryToEntity))
                    ApiResult.Success(result.data.toPaged(summaries, offset = 0, limit = limit))
                }
                is ApiResult.Failure -> result
            }
        }

    override suspend fun loadMoreLists(offset: Int, limit: Int): ApiResult<Paged<ListSummary>> =
        withContext(dispatchers.io) {
            when (val result = safeApiCall(json) { api.getLists(limit = limit, offset = offset) }) {
                is ApiResult.Success -> {
                    val summaries = result.data.items.map(ListMapper::summaryFromDto)
                    listDao.upsertAll(summaries.map(ListMapper::summaryToEntity))
                    ApiResult.Success(result.data.toPaged(summaries, offset = offset, limit = limit))
                }
                is ApiResult.Failure -> result
            }
        }

    override suspend fun searchLists(query: String, limit: Int): ApiResult<List<ListSummary>> =
        withContext(dispatchers.io) {
            safeApiCall(json) { api.searchLists(query = query, limit = limit, offset = 0) }
                .map { response -> response.items.map(ListMapper::summaryFromDto) }
        }

    override suspend fun createList(
        title: String,
        description: String?,
        isPublic: Boolean,
    ): ApiResult<ListSummary> = withContext(dispatchers.io) {
        val body = CreateListRequest(title = title, description = description, isPublic = isPublic)
        when (val result = safeApiCall(json) { api.createList(body) }) {
            is ApiResult.Success -> {
                val dto = result.data.list ?: result.data.data
                    ?: return@withContext ApiResult.Success(
                        ListSummary(
                            id = "", title = title, description = description,
                            itemCount = 0, folderId = null, isPublic = isPublic, updatedAt = null,
                        ),
                    )
                val summary = ListMapper.summaryFromDto(dto)
                listDao.upsert(ListMapper.summaryToEntity(summary))
                ApiResult.Success(summary)
            }
            is ApiResult.Failure -> result
        }
    }

    override suspend fun deleteList(id: String): ApiResult<Unit> = withContext(dispatchers.io) {
        when (val result = safeApiCall(json) { api.deleteList(id) }) {
            is ApiResult.Success -> {
                listDao.deleteById(id)
                ApiResult.Success(Unit)
            }
            is ApiResult.Failure -> result
        }
    }

    override suspend fun getListDetail(id: String, rowLimit: Int): ApiResult<ListDetail> =
        withContext(dispatchers.io) {
            // 1) Metadata.
            val listResult = safeApiCall(json) { api.getList(id) }
            val listDto: ListDto = when (listResult) {
                is ApiResult.Success -> listResult.data.list ?: listResult.data.data
                    ?: return@withContext ApiResult.Failure(
                        com.interlinedlist.android.core.common.result.AppError.NotFound("List not found"),
                    )
                is ApiResult.Failure -> return@withContext listResult
            }

            // 2) Schema (dynamic DSL). Prefer the dedicated endpoint; fall back to
            //    any schema inlined on the list payload.
            val schema: ListSchema = when (val schemaResult = safeApiCall(json) { api.getSchema(id) }) {
                is ApiResult.Success -> SchemaMapper.fromJson(schemaResult.data)
                is ApiResult.Failure -> SchemaMapper.fromJson(listDto.schema)
            }

            // 3) First page of rows.
            val rowsResult = safeApiCall(json) { api.getRows(id, limit = rowLimit, offset = 0) }
            val rows: List<ListRow> = when (rowsResult) {
                is ApiResult.Success -> rowsResult.data.items.map(RowMapper::fromDto)
                is ApiResult.Failure -> return@withContext rowsResult
            }

            val summary = ListMapper.summaryFromDto(listDto)
            listDao.upsert(ListMapper.summaryToEntity(summary))
            ApiResult.Success(ListDetail(summary = summary, schema = schema, rows = rows))
        }

    override suspend fun addRow(listId: String, values: Map<String, String>): ApiResult<ListRow> =
        withContext(dispatchers.io) {
            safeApiCall(json) { api.createRow(listId, RowWriteRequest(values.toJsonData())) }
                .map { it.row ?: it.data ?: RowDto(id = "", data = kotlinx.serialization.json.JsonObject(emptyMap())) }
                .map(RowMapper::fromDto)
        }

    override suspend fun updateRow(
        listId: String,
        rowId: String,
        values: Map<String, String>,
    ): ApiResult<ListRow> = withContext(dispatchers.io) {
        safeApiCall(json) { api.updateRow(listId, rowId, RowWriteRequest(values.toJsonData())) }
            .map { it.row ?: it.data ?: RowDto(id = rowId, data = kotlinx.serialization.json.JsonObject(emptyMap())) }
            .map(RowMapper::fromDto)
    }

    override suspend fun deleteRow(listId: String, rowId: String): ApiResult<Unit> =
        withContext(dispatchers.io) {
            safeApiCall(json) { api.deleteRow(listId, rowId) }.map { }
        }

    override suspend fun getFolders(): ApiResult<List<ListFolder>> = withContext(dispatchers.io) {
        safeApiCall(json) { api.getFolders() }
            .map { response -> response.items.map(ListMapper::folderFromDto) }
    }

    override suspend fun createFolder(name: String, parentId: String?): ApiResult<ListFolder> =
        withContext(dispatchers.io) {
            safeApiCall(json) { api.createFolder(CreateFolderRequest(name = name, parentId = parentId)) }
                .map(ListMapper::folderFromDto)
        }

    override suspend fun updateSchema(listId: String, schema: ListSchema): ApiResult<ListSchema> =
        withContext(dispatchers.io) {
            // The API expects the schema as a serialised DSL string; send the edited
            // fields as the canonical array DSL and re-parse the response.
            val dsl = SchemaMapper.toDsl(schema).toString()
            when (val result = safeApiCall(json) { api.updateSchema(listId, UpdateSchemaRequest(dsl)) }) {
                is ApiResult.Success -> {
                    val returned = result.data.schema ?: result.data.data
                    // Echo the round-tripped schema when present; otherwise trust what we sent.
                    val parsed = if (returned != null) SchemaMapper.fromJson(returned) else schema
                    ApiResult.Success(parsed)
                }
                is ApiResult.Failure -> result
            }
        }

    override suspend fun refreshGithubList(listId: String): ApiResult<RefreshResult> =
        withContext(dispatchers.io) {
            safeApiCall(json) { api.refreshList(listId) }
                .map(ConnectionMapper::refreshFromDto)
        }

    override suspend fun getWatchers(listId: String, limit: Int): ApiResult<List<Watcher>> =
        withContext(dispatchers.io) {
            safeApiCall(json) { api.getWatchers(listId, limit = limit, offset = 0) }
                .map { response -> response.items.mapNotNull(WatcherMapper::watcherFromDto) }
        }

    override suspend fun isWatching(listId: String): ApiResult<Boolean> =
        withContext(dispatchers.io) {
            safeApiCall(json) { api.getWatchingStatus(listId) }
                .map { it.isWatchingResolved }
        }

    override suspend fun searchWatcherCandidates(
        listId: String,
        query: String,
        limit: Int,
    ): ApiResult<List<WatcherCandidate>> = withContext(dispatchers.io) {
        safeApiCall(json) {
            api.searchWatcherUsers(
                id = listId,
                search = query,
                excludeWatchers = true,
                limit = limit,
                offset = 0,
            )
        }.map { response -> response.items.map(WatcherMapper::candidateFromDto) }
    }

    override suspend fun addWatcher(
        listId: String,
        userId: String,
        role: WatcherRole,
    ): ApiResult<Unit> = withContext(dispatchers.io) {
        safeApiCall(json) {
            api.addWatcher(listId, AddWatcherRequest(userId = userId, role = role.apiValue))
        }.map { }
    }

    override suspend fun updateWatcherRole(
        listId: String,
        userId: String,
        role: WatcherRole,
    ): ApiResult<Unit> = withContext(dispatchers.io) {
        safeApiCall(json) {
            api.updateWatcherRole(listId, userId, UpdateWatcherRoleRequest(role = role.apiValue))
        }.map { }
    }

    override suspend fun removeWatcher(listId: String, userId: String): ApiResult<Unit> =
        withContext(dispatchers.io) {
            safeApiCall(json) { api.removeWatcher(listId, userId) }.map { }
        }

    override suspend fun getConnections(): ApiResult<List<ListConnection>> =
        withContext(dispatchers.io) {
            safeApiCall(json) { api.getConnections() }
                .map { response -> response.items.map(ConnectionMapper::connectionFromDto) }
        }

    override suspend fun createConnection(
        fromListId: String,
        toListId: String,
        label: String?,
    ): ApiResult<ListConnection> = withContext(dispatchers.io) {
        val body = CreateConnectionRequest(
            fromListId = fromListId,
            toListId = toListId,
            label = label?.takeIf { it.isNotBlank() },
        )
        when (val result = safeApiCall(json) { api.createConnection(body) }) {
            is ApiResult.Success -> {
                val dto = result.data.connection ?: result.data.data
                    ?: return@withContext ApiResult.Success(
                        ListConnection(
                            id = "", fromListId = fromListId, toListId = toListId,
                            label = label?.takeIf { it.isNotBlank() },
                            fromListTitle = fromListId, toListTitle = toListId,
                        ),
                    )
                ApiResult.Success(ConnectionMapper.connectionFromDto(dto))
            }
            is ApiResult.Failure -> result
        }
    }

    override suspend fun deleteConnection(id: String): ApiResult<Unit> =
        withContext(dispatchers.io) {
            safeApiCall(json) { api.deleteConnection(id) }.map { }
        }

    override suspend fun getShareLinks(listId: String): ApiResult<List<ShareLink>> =
        withContext(dispatchers.io) {
            safeApiCall(json) { api.getShareLinks(listId) }
                .map { response -> response.items.map(ShareMapper::linkFromDto) }
        }

    override suspend fun createShareLink(listId: String, role: ShareRole): ApiResult<ShareLink> =
        withContext(dispatchers.io) {
            when (val result = safeApiCall(json) {
                api.createShareLink(listId, CreateShareLinkRequest(role = role.apiValue))
            }) {
                is ApiResult.Success -> {
                    val dto = result.data.linkOrSelf
                        ?: return@withContext ApiResult.Failure(
                            com.interlinedlist.android.core.common.result.AppError.Unknown(
                                "Share link create returned no token",
                            ),
                        )
                    ApiResult.Success(ShareMapper.linkFromDto(dto))
                }
                is ApiResult.Failure -> result
            }
        }

    override suspend fun revokeShareLink(listId: String, token: String): ApiResult<Unit> =
        withContext(dispatchers.io) {
            safeApiCall(json) { api.revokeShareLink(listId, token) }.map { }
        }

    override suspend fun getSharedWithMe(): ApiResult<List<SharedList>> =
        withContext(dispatchers.io) {
            safeApiCall(json) { api.getWatchingLists() }
                .map { response -> response.items.map(ShareMapper::sharedFromDto) }
        }

    override suspend fun resolveSharedList(token: String): ApiResult<SharedListResolution> =
        withContext(dispatchers.io) {
            safeApiCall(json) { api.resolveSharedList(token) }
                .map { response -> ShareMapper.resolutionFromResponse(token, response) }
        }

    override suspend fun claimSharedList(token: String): ApiResult<Unit> =
        withContext(dispatchers.io) {
            safeApiCall(json) { api.claimSharedList(token) }.map { }
        }

    /** Blank form fields are dropped so we don't overwrite server values with empty strings. */
    private fun Map<String, String>.toJsonData(): Map<String, JsonElement> =
        filterValues { it.isNotBlank() }
            .mapValues { (_, value) -> JsonPrimitive(value) as JsonElement }
}

/** Builds a [Paged] from the response's pagination block, tolerating its absence. */
private fun com.interlinedlist.android.feature.lists.data.remote.dto.ListsResponse.toPaged(
    items: List<ListSummary>,
    offset: Int,
    limit: Int,
): Paged<ListSummary> {
    val page = pagination
    val nextOffset = offset + items.size
    val hasMore = page?.hasMore ?: (page?.let { nextOffset < it.total } ?: (items.size >= limit))
    val total = page?.total ?: nextOffset
    return Paged(items = items, hasMore = hasMore, total = total, offset = nextOffset)
}
