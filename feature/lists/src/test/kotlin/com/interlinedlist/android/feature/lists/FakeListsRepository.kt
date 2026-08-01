package com.interlinedlist.android.feature.lists

import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.lists.data.ListsRepository
import com.interlinedlist.android.feature.lists.domain.Contributor
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
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [ListsRepository] for ViewModel tests. The cache is a StateFlow so
 * tests can assert the offline-first stream, and each operation's result is
 * configurable to exercise success / failure / subscription-gate paths.
 */
class FakeListsRepository : ListsRepository {

    val cache = MutableStateFlow<List<ListSummary>>(emptyList())

    var refreshResult: ApiResult<Paged<ListSummary>> =
        ApiResult.Success(Paged(emptyList(), hasMore = false, total = 0, offset = 0))
    var loadMoreResult: ApiResult<Paged<ListSummary>> = refreshResult
    var searchResult: ApiResult<List<ListSummary>> = ApiResult.Success(emptyList())
    var createResult: ApiResult<ListSummary>? = null
    var updateListResult: ApiResult<ListSummary>? = null
    var deleteResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var detailResult: ApiResult<ListDetail>? = null
    var getRowResult: ApiResult<ListRow>? = null
    var addRowResult: ApiResult<ListRow>? = null
    var updateRowResult: ApiResult<ListRow>? = null
    var deleteRowResult: ApiResult<Unit> = ApiResult.Success(Unit)

    // Folder management + contributors.
    var foldersResult: ApiResult<List<ListFolder>> = ApiResult.Success(emptyList())
    var updateFolderResult: ApiResult<ListFolder>? = null
    var deleteFolderResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var contributorsResult: ApiResult<List<Contributor>> = ApiResult.Success(emptyList())

    // Round-2 deferred features.
    var updateSchemaResult: ApiResult<ListSchema>? = null
    var refreshGithubResult: ApiResult<RefreshResult> =
        ApiResult.Success(RefreshResult(message = null, added = 0, updated = 0, removed = 0))
    var watchersResult: ApiResult<List<Watcher>> = ApiResult.Success(emptyList())
    var isWatchingResult: ApiResult<Boolean> = ApiResult.Success(false)
    var candidatesResult: ApiResult<List<WatcherCandidate>> = ApiResult.Success(emptyList())
    var addWatcherResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var updateWatcherRoleResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var removeWatcherResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var connectionsResult: ApiResult<List<ListConnection>> = ApiResult.Success(emptyList())
    var createConnectionResult: ApiResult<ListConnection>? = null
    var deleteConnectionResult: ApiResult<Unit> = ApiResult.Success(Unit)

    // Sharing.
    var shareLinksResult: ApiResult<List<ShareLink>> = ApiResult.Success(emptyList())
    var createShareLinkResult: ApiResult<ShareLink>? = null
    var revokeShareLinkResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var sharedWithMeResult: ApiResult<List<SharedList>> = ApiResult.Success(emptyList())
    var resolveSharedResult: ApiResult<SharedListResolution>? = null
    var claimSharedResult: ApiResult<Unit> = ApiResult.Success(Unit)

    var refreshCount = 0
    var loadMoreCount = 0
    var updateListCount = 0
    var updateFolderCount = 0
    var deleteFolderCount = 0
    var lastUpdatedTitle: String? = null
    var lastUpdatedDescription: String? = null
    var lastUpdatedIsPublic: Boolean? = null
    var lastUpdatedFolderName: String? = null
    var lastUpdatedFolderParentId: String? = null
    var lastDeletedFolderId: String? = null
    var updateSchemaCount = 0
    var refreshGithubCount = 0
    var addWatcherCount = 0
    var removeWatcherCount = 0
    var createShareLinkCount = 0
    var revokeShareLinkCount = 0
    var claimSharedCount = 0
    var lastSchemaUpdate: ListSchema? = null
    var lastWatcherSearch: String? = null
    var lastCreatedShareRole: ShareRole? = null
    var lastRevokedToken: String? = null
    var lastResolvedToken: String? = null
    var lastClaimedToken: String? = null

    override fun observeLists(): Flow<List<ListSummary>> = cache

    override suspend fun refreshLists(limit: Int): ApiResult<Paged<ListSummary>> {
        refreshCount++
        (refreshResult as? ApiResult.Success)?.let { cache.value = it.data.items }
        return refreshResult
    }

    override suspend fun loadMoreLists(offset: Int, limit: Int): ApiResult<Paged<ListSummary>> {
        loadMoreCount++
        (loadMoreResult as? ApiResult.Success)?.let { cache.value = cache.value + it.data.items }
        return loadMoreResult
    }

    override suspend fun searchLists(query: String, limit: Int): ApiResult<List<ListSummary>> = searchResult

    override suspend fun createList(title: String, description: String?, isPublic: Boolean): ApiResult<ListSummary> =
        createResult ?: ApiResult.Success(
            ListSummary("new", title, description, 0, null, isPublic, null),
        )

    override suspend fun updateList(
        id: String,
        title: String?,
        description: String?,
        isPublic: Boolean?,
        folderId: String?,
    ): ApiResult<ListSummary> {
        updateListCount++
        lastUpdatedTitle = title
        lastUpdatedDescription = description
        lastUpdatedIsPublic = isPublic
        val result = updateListResult ?: run {
            val current = cache.value.firstOrNull { it.id == id }
            ApiResult.Success(
                ListSummary(
                    id = id,
                    title = title ?: current?.title.orEmpty(),
                    description = description ?: current?.description,
                    itemCount = current?.itemCount ?: 0,
                    folderId = folderId ?: current?.folderId,
                    isPublic = isPublic ?: current?.isPublic ?: false,
                    updatedAt = current?.updatedAt,
                ),
            )
        }
        (result as? ApiResult.Success)?.let { success ->
            cache.value = cache.value.map { if (it.id == id) success.data else it }
        }
        return result
    }

    override suspend fun deleteList(id: String): ApiResult<Unit> {
        if (deleteResult is ApiResult.Success) cache.value = cache.value.filterNot { it.id == id }
        return deleteResult
    }

    override suspend fun getListDetail(id: String, rowLimit: Int): ApiResult<ListDetail> =
        detailResult ?: ApiResult.Success(
            ListDetail(
                summary = ListSummary(id, "Untitled", null, 0, null, false, null),
                schema = ListSchema.EMPTY,
                rows = emptyList(),
            ),
        )

    override suspend fun getRow(listId: String, rowId: String): ApiResult<ListRow> =
        getRowResult ?: ApiResult.Success(ListRow(rowId, emptyMap()))

    override suspend fun addRow(listId: String, values: Map<String, String>): ApiResult<ListRow> =
        addRowResult ?: ApiResult.Success(ListRow("row-new", values))

    override suspend fun updateRow(listId: String, rowId: String, values: Map<String, String>): ApiResult<ListRow> =
        updateRowResult ?: ApiResult.Success(ListRow(rowId, values))

    override suspend fun deleteRow(listId: String, rowId: String): ApiResult<Unit> = deleteRowResult

    override suspend fun getFolders(): ApiResult<List<ListFolder>> = foldersResult

    override suspend fun createFolder(name: String, parentId: String?): ApiResult<ListFolder> =
        ApiResult.Success(ListFolder("f", name, parentId))

    override suspend fun updateFolder(
        id: String,
        name: String?,
        parentId: String?,
    ): ApiResult<ListFolder> {
        updateFolderCount++
        lastUpdatedFolderName = name
        lastUpdatedFolderParentId = parentId
        return updateFolderResult ?: ApiResult.Success(ListFolder(id, name.orEmpty(), parentId))
    }

    override suspend fun deleteFolder(id: String): ApiResult<Unit> {
        deleteFolderCount++
        lastDeletedFolderId = id
        return deleteFolderResult
    }

    override suspend fun getContributors(listId: String): ApiResult<List<Contributor>> = contributorsResult

    override suspend fun updateSchema(listId: String, schema: ListSchema): ApiResult<ListSchema> {
        updateSchemaCount++
        lastSchemaUpdate = schema
        return updateSchemaResult ?: ApiResult.Success(schema)
    }

    override suspend fun refreshGithubList(listId: String): ApiResult<RefreshResult> {
        refreshGithubCount++
        return refreshGithubResult
    }

    override suspend fun getWatchers(listId: String, limit: Int): ApiResult<List<Watcher>> = watchersResult

    override suspend fun isWatching(listId: String): ApiResult<Boolean> = isWatchingResult

    override suspend fun searchWatcherCandidates(
        listId: String,
        query: String,
        limit: Int,
    ): ApiResult<List<WatcherCandidate>> {
        lastWatcherSearch = query
        return candidatesResult
    }

    override suspend fun addWatcher(listId: String, userId: String, role: WatcherRole): ApiResult<Unit> {
        addWatcherCount++
        return addWatcherResult
    }

    override suspend fun updateWatcherRole(
        listId: String,
        userId: String,
        role: WatcherRole,
    ): ApiResult<Unit> = updateWatcherRoleResult

    override suspend fun removeWatcher(listId: String, userId: String): ApiResult<Unit> {
        removeWatcherCount++
        return removeWatcherResult
    }

    override suspend fun getConnections(): ApiResult<List<ListConnection>> = connectionsResult

    override suspend fun createConnection(
        fromListId: String,
        toListId: String,
        label: String?,
    ): ApiResult<ListConnection> = createConnectionResult
        ?: ApiResult.Success(ListConnection("c-new", fromListId, toListId, label, fromListId, toListId))

    override suspend fun deleteConnection(id: String): ApiResult<Unit> = deleteConnectionResult

    override suspend fun getShareLinks(listId: String): ApiResult<List<ShareLink>> = shareLinksResult

    override suspend fun createShareLink(listId: String, role: ShareRole): ApiResult<ShareLink> {
        createShareLinkCount++
        lastCreatedShareRole = role
        return createShareLinkResult ?: ApiResult.Success(
            ShareLink("link-new", "token-new", role, null, null, null),
        )
    }

    override suspend fun revokeShareLink(listId: String, token: String): ApiResult<Unit> {
        revokeShareLinkCount++
        lastRevokedToken = token
        return revokeShareLinkResult
    }

    override suspend fun getSharedWithMe(): ApiResult<List<SharedList>> = sharedWithMeResult

    override suspend fun resolveSharedList(token: String): ApiResult<SharedListResolution> {
        lastResolvedToken = token
        return resolveSharedResult ?: ApiResult.Success(
            SharedListResolution(token, "L", "Untitled", null, null, ShareRole.VIEW, emptyList()),
        )
    }

    override suspend fun claimSharedList(token: String): ApiResult<Unit> {
        claimSharedCount++
        lastClaimedToken = token
        return claimSharedResult
    }

    companion object {
        fun subscriptionFailure(): ApiResult.Failure =
            ApiResult.Failure(AppError.SubscriptionRequired("Lists require an active subscription"))
    }
}
