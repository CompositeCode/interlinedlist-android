package com.interlinedlist.android.feature.lists

import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.lists.data.ListsRepository
import com.interlinedlist.android.feature.lists.domain.Contributor
import com.interlinedlist.android.feature.lists.domain.GITHUB_SOURCE_ISSUES
import com.interlinedlist.android.feature.lists.domain.ListConnection
import com.interlinedlist.android.feature.lists.domain.ListDetail
import com.interlinedlist.android.feature.lists.domain.ListFolder
import com.interlinedlist.android.feature.lists.domain.ListRow
import com.interlinedlist.android.feature.lists.domain.ListSchema
import com.interlinedlist.android.feature.lists.domain.ListSource
import com.interlinedlist.android.feature.lists.domain.ListSummary
import com.interlinedlist.android.feature.lists.domain.ListView
import com.interlinedlist.android.feature.lists.domain.ListViewConfig
import com.interlinedlist.android.feature.lists.domain.ListViewScope
import com.interlinedlist.android.feature.lists.domain.Paged
import com.interlinedlist.android.feature.lists.domain.RefreshResult
import com.interlinedlist.android.feature.lists.domain.ShareLink
import com.interlinedlist.android.feature.lists.domain.ShareRole
import com.interlinedlist.android.feature.lists.domain.SharedList
import com.interlinedlist.android.feature.lists.domain.SharedListResolution
import com.interlinedlist.android.feature.lists.domain.Watcher
import com.interlinedlist.android.feature.lists.domain.WatcherCandidate
import com.interlinedlist.android.feature.lists.domain.WatcherRole
import com.interlinedlist.android.feature.lists.domain.isValidGithubRepo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonObject

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
    var parentChainResult: ApiResult<List<ListSummary>> = ApiResult.Success(emptyList())
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

    // Saved views.
    var viewsResult: ApiResult<List<ListView>> = ApiResult.Success(emptyList())
    var createViewResult: ApiResult<ListView>? = null
    var updateViewResult: ApiResult<ListView>? = null
    var forkViewResult: ApiResult<ListView>? = null
    var deleteViewResult: ApiResult<Unit> = ApiResult.Success(Unit)

    // Sharing.
    var shareLinksResult: ApiResult<List<ShareLink>> = ApiResult.Success(emptyList())
    var createShareLinkResult: ApiResult<ShareLink>? = null
    var revokeShareLinkResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var sharedWithMeResult: ApiResult<List<SharedList>> = ApiResult.Success(emptyList())
    var resolveSharedResult: ApiResult<SharedListResolution>? = null
    var claimSharedResult: ApiResult<Unit> = ApiResult.Success(Unit)

    var refreshCount = 0
    var loadMoreCount = 0
    var parentChainCount = 0
    var lastParentChainId: String? = null
    var lastCreateParentId: String? = null
    var lastCreateFolderId: String? = null
    var lastCreateMessageId: String? = null
    var lastCreateInitialRows: List<Map<String, String>>? = null
    var lastCreateMetadata: JsonObject? = null
    var lastCreateSource: ListSource? = null
    var lastCreateGithubRepo: String? = null
    var lastCreateGithubSource: String? = null
    var lastUpdatedParentId: String? = null
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
    var viewsCount = 0
    var lastCreatedViewName: String? = null
    var lastCreatedViewScope: ListViewScope? = null
    var lastUpdatedViewId: String? = null
    var lastUpdatedViewName: String? = null
    var lastUpdatedViewConfig: ListViewConfig? = null
    var lastUpdatedViewIsDefault: Boolean? = null
    var lastForkedViewId: String? = null
    var lastDeletedViewId: String? = null
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

    override suspend fun createList(
        title: String,
        description: String?,
        isPublic: Boolean,
        parentId: String?,
        folderId: String?,
        messageId: String?,
        initialRows: List<Map<String, String>>?,
        metadata: JsonObject?,
        source: ListSource?,
        githubRepo: String?,
        githubSource: String?,
    ): ApiResult<ListSummary> {
        lastCreateParentId = parentId
        lastCreateFolderId = folderId
        lastCreateMessageId = messageId
        lastCreateInitialRows = initialRows
        lastCreateMetadata = metadata
        lastCreateSource = source
        lastCreateGithubRepo = githubRepo
        lastCreateGithubSource = githubSource
        return createResult ?: ApiResult.Success(
            ListSummary(
                id = "new",
                title = title,
                description = description,
                itemCount = 0,
                folderId = folderId,
                isPublic = isPublic,
                updatedAt = null,
                parentId = parentId,
                source = source ?: ListSource.LOCAL,
                githubRepo = githubRepo,
            ),
        )
    }

    /** Mirrors the real repository: validates the repo then delegates to [createList]. */
    override suspend fun createGithubList(
        repo: String,
        title: String,
        isPublic: Boolean,
        parentId: String?,
    ): ApiResult<ListSummary> {
        if (!isValidGithubRepo(repo)) {
            return ApiResult.Failure(AppError.Unknown("Pick a repository in owner/repo form."))
        }
        return createList(
            title = title,
            isPublic = isPublic,
            parentId = parentId,
            source = ListSource.GITHUB,
            githubRepo = repo,
            githubSource = GITHUB_SOURCE_ISSUES,
        )
    }

    override suspend fun createListFromMessage(
        messageId: String,
        title: String,
        description: String?,
    ): ApiResult<ListSummary> = createList(title = title, description = description, messageId = messageId)

    override suspend fun getParentChain(parentId: String): ApiResult<List<ListSummary>> {
        parentChainCount++
        lastParentChainId = parentId
        return parentChainResult
    }

    override suspend fun updateList(
        id: String,
        title: String?,
        description: String?,
        isPublic: Boolean?,
        folderId: String?,
        parentId: String?,
    ): ApiResult<ListSummary> {
        updateListCount++
        lastUpdatedTitle = title
        lastUpdatedDescription = description
        lastUpdatedIsPublic = isPublic
        lastUpdatedParentId = parentId
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
                    parentId = parentId ?: current?.parentId,
                    source = current?.source ?: ListSource.LOCAL,
                    githubRepo = current?.githubRepo,
                    githubRepoPrivate = current?.githubRepoPrivate,
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

    override suspend fun getViews(listId: String): ApiResult<List<ListView>> {
        viewsCount++
        return viewsResult
    }

    override suspend fun createView(
        listId: String,
        name: String,
        scope: ListViewScope?,
        config: ListViewConfig?,
        isDefault: Boolean,
    ): ApiResult<ListView> {
        lastCreatedViewName = name
        lastCreatedViewScope = scope
        // Mirrors the real repository: an absent scope never reaches the network.
        if (scope == null) return ApiResult.Failure(AppError.Unknown("Choose whether the view is shared or personal."))
        return createViewResult ?: ApiResult.Success(
            ListView("v-new", listId, "me", name, scope, ListViewConfig.DEFAULT, isDefault, 0),
        )
    }

    override suspend fun updateView(
        listId: String,
        viewId: String,
        name: String?,
        config: ListViewConfig?,
        isDefault: Boolean?,
    ): ApiResult<ListView> {
        lastUpdatedViewId = viewId
        lastUpdatedViewName = name
        lastUpdatedViewConfig = config
        lastUpdatedViewIsDefault = isDefault
        return updateViewResult ?: ApiResult.Success(
            ListView(viewId, listId, "me", name.orEmpty(), ListViewScope.PERSONAL, ListViewConfig.DEFAULT, isDefault == true, 0),
        )
    }

    override suspend fun forkView(listId: String, viewId: String): ApiResult<ListView> {
        lastForkedViewId = viewId
        return forkViewResult ?: ApiResult.Success(
            ListView("$viewId-copy", listId, "me", "Copy", ListViewScope.PERSONAL, ListViewConfig.DEFAULT, false, 0),
        )
    }

    override suspend fun deleteView(listId: String, viewId: String): ApiResult<Unit> {
        lastDeletedViewId = viewId
        return deleteViewResult
    }

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
