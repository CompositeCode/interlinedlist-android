package com.interlinedlist.android.feature.lists.data

import com.interlinedlist.android.core.common.result.ApiResult
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

/**
 * Offline-first access to the Lists domain. The index streams from Room (the
 * source of truth) via [observeLists]; refresh/load-more calls update the cache
 * and report the pagination state so the UI knows whether more pages remain.
 */
interface ListsRepository {

    /** Cached lists, emitted from Room and re-emitted on every local change. */
    fun observeLists(): Flow<List<ListSummary>>

    /** Fetches the first page from the API and replaces the cache. Returns pagination. */
    suspend fun refreshLists(limit: Int = DEFAULT_PAGE_SIZE): ApiResult<Paged<ListSummary>>

    /** Fetches a further page and appends it to the cache. */
    suspend fun loadMoreLists(offset: Int, limit: Int = DEFAULT_PAGE_SIZE): ApiResult<Paged<ListSummary>>

    /** Server-side search (not cached) by title/description. */
    suspend fun searchLists(query: String, limit: Int = DEFAULT_PAGE_SIZE): ApiResult<List<ListSummary>>

    /** Creates a list; caches the result and returns its summary. */
    suspend fun createList(title: String, description: String?, isPublic: Boolean): ApiResult<ListSummary>

    /**
     * Updates a list's metadata (title/description/visibility/folder). Only the
     * supplied fields change; the returned summary reflects the server's echo and
     * the cache is updated to match.
     */
    suspend fun updateList(
        id: String,
        title: String? = null,
        description: String? = null,
        isPublic: Boolean? = null,
        folderId: String? = null,
    ): ApiResult<ListSummary>

    /** Deletes a list and evicts it from the cache. */
    suspend fun deleteList(id: String): ApiResult<Unit>

    /** Loads a list's metadata, schema, and first page of rows for the detail screen. */
    suspend fun getListDetail(id: String, rowLimit: Int = DEFAULT_PAGE_SIZE): ApiResult<ListDetail>

    /** Fetches a single data row by id (e.g. for a row-detail view). */
    suspend fun getRow(listId: String, rowId: String): ApiResult<ListRow>

    suspend fun addRow(listId: String, values: Map<String, String>): ApiResult<ListRow>

    suspend fun updateRow(listId: String, rowId: String, values: Map<String, String>): ApiResult<ListRow>

    suspend fun deleteRow(listId: String, rowId: String): ApiResult<Unit>

    suspend fun getFolders(): ApiResult<List<ListFolder>>

    suspend fun createFolder(name: String, parentId: String?): ApiResult<ListFolder>

    /**
     * Renames and/or moves a folder. Pass only the fields to change; the returned
     * folder reflects the server's echo.
     */
    suspend fun updateFolder(
        id: String,
        name: String? = null,
        parentId: String? = null,
    ): ApiResult<ListFolder>

    /** Soft-deletes a folder; its lists move to the root on the server. */
    suspend fun deleteFolder(id: String): ApiResult<Unit>

    /** People who have contributed rows to a list, ranked (read-only). */
    suspend fun getContributors(listId: String): ApiResult<List<Contributor>>

    /** Replaces a list's schema (add/edit/remove columns) and returns the parsed result. */
    suspend fun updateSchema(listId: String, schema: ListSchema): ApiResult<ListSchema>

    /** Manually re-syncs a GitHub-backed list and reports what changed. */
    suspend fun refreshGithubList(listId: String): ApiResult<RefreshResult>

    /** Watchers of a list (users granted access), with their roles. */
    suspend fun getWatchers(listId: String, limit: Int = DEFAULT_PAGE_SIZE): ApiResult<List<Watcher>>

    /** Whether the current user is watching [listId]. */
    suspend fun isWatching(listId: String): ApiResult<Boolean>

    /** Searches users who could be added as watchers (excludes existing watchers). */
    suspend fun searchWatcherCandidates(
        listId: String,
        query: String,
        limit: Int = DEFAULT_PAGE_SIZE,
    ): ApiResult<List<WatcherCandidate>>

    /** Adds a user as a watcher with the given role. */
    suspend fun addWatcher(listId: String, userId: String, role: WatcherRole): ApiResult<Unit>

    /** Changes an existing watcher's role. */
    suspend fun updateWatcherRole(listId: String, userId: String, role: WatcherRole): ApiResult<Unit>

    /** Removes a user's access to the list. */
    suspend fun removeWatcher(listId: String, userId: String): ApiResult<Unit>

    /** All connections between the user's lists. */
    suspend fun getConnections(): ApiResult<List<ListConnection>>

    /** Creates a labelled link from one list to another. */
    suspend fun createConnection(
        fromListId: String,
        toListId: String,
        label: String?,
    ): ApiResult<ListConnection>

    /** Removes a connection between lists. */
    suspend fun deleteConnection(id: String): ApiResult<Unit>

    // --- Sharing -----------------------------------------------------------

    /** Existing public share links for a list. */
    suspend fun getShareLinks(listId: String): ApiResult<List<ShareLink>>

    /** Creates a share link granting [role]; returns the created link. */
    suspend fun createShareLink(listId: String, role: ShareRole): ApiResult<ShareLink>

    /** Revokes a share link by its token. */
    suspend fun revokeShareLink(listId: String, token: String): ApiResult<Unit>

    /** Lists shared with the current user by other owners, with the granted role. */
    suspend fun getSharedWithMe(): ApiResult<List<SharedList>>

    /** Resolves a public `…/shared/{token}` link to a read-only preview. */
    suspend fun resolveSharedList(token: String): ApiResult<SharedListResolution>

    /** Claims edit/admin access to a shared list via its token. */
    suspend fun claimSharedList(token: String): ApiResult<Unit>

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}
