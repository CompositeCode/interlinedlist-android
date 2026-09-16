package com.interlinedlist.android.feature.lists.data

import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.lists.domain.Contributor
import com.interlinedlist.android.feature.lists.domain.InviteRole
import com.interlinedlist.android.feature.lists.domain.ListConnection
import com.interlinedlist.android.feature.lists.domain.ListDetail
import com.interlinedlist.android.feature.lists.domain.ListFolder
import com.interlinedlist.android.feature.lists.domain.ListInvite
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
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

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

    /**
     * Creates a list; caches the result and returns its summary.
     *
     * Everything past [isPublic] is an optional creation option the API accepts
     * and is omitted from the request when null, so callers that only need a
     * title/description send exactly what they always sent:
     * - [parentId] nests the new list under an existing one (see [getParentChain]).
     * - [folderId] files it into a folder.
     * - [messageId] records the message the list was created from.
     * - [initialRows] seeds starter rows, each a column-key → value map, exactly
     *   as [addRow] sends a row.
     * - [metadata] is a free-form JSON object passed through untouched.
     * - [source] marks where the rows come from (defaults to the server's own).
     * - [githubRepo] (`"owner/repo"`) and [githubSource] configure a
     *   [ListSource.GITHUB] list; see [createGithubList], which is what callers
     *   should reach for.
     */
    suspend fun createList(
        title: String,
        description: String? = null,
        isPublic: Boolean = false,
        parentId: String? = null,
        folderId: String? = null,
        messageId: String? = null,
        initialRows: List<Map<String, String>>? = null,
        metadata: JsonObject? = null,
        source: ListSource? = null,
        githubRepo: String? = null,
        githubSource: String? = null,
    ): ApiResult<ListSummary>

    /**
     * Creates a list backed by a GitHub repository's issues.
     *
     * Sends `source: "github"` together with [repo] as `githubRepo` and
     * `githubSource: "issues"`; the server rejects a GitHub source without a
     * well-formed `owner/repo` (`400 bad_request`), so [repo] is validated here
     * before the request is spent.
     */
    suspend fun createGithubList(
        repo: String,
        title: String,
        isPublic: Boolean = false,
        parentId: String? = null,
    ): ApiResult<ListSummary>

    /**
     * Creates a list from a message: the message becomes the new list's
     * description and the API keeps the link via `messageId`.
     *
     * The entry point that offers this on a message lives in `:feature:messages`
     * (see the cross-object "create from" work); this is the call it makes.
     */
    suspend fun createListFromMessage(
        messageId: String,
        title: String,
        description: String?,
    ): ApiResult<ListSummary>

    /**
     * Resolves a list's ancestry for breadcrumb navigation: fetches [parentId]
     * and keeps walking up, returning the chain ordered root → immediate parent.
     *
     * A level the server cannot serve truncates the chain rather than failing,
     * since a breadcrumb is navigation decoration; a failure is only reported
     * when nothing at all could be resolved.
     */
    suspend fun getParentChain(parentId: String): ApiResult<List<ListSummary>>

    /**
     * Updates a list's metadata (title/description/visibility/folder/parent).
     * Only the supplied fields change; the returned summary reflects the server's
     * echo and the cache is updated to match.
     *
     * [parentId] is the one editable property of a GitHub-backed list, whose
     * columns are fixed by GitHub.
     */
    suspend fun updateList(
        id: String,
        title: String? = null,
        description: String? = null,
        isPublic: Boolean? = null,
        folderId: String? = null,
        parentId: String? = null,
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

    // --- Saved views -------------------------------------------------------

    /**
     * Saved views for a list: every shared view plus the caller's own personal
     * ones, in the order the server returns them.
     */
    suspend fun getViews(listId: String): ApiResult<List<ListView>>

    /**
     * Creates a saved view. [scope] is nullable because the UI can ask before the
     * user has chosen one; a missing or unrecognised scope fails locally without
     * spending a request, since the server rejects it with a 400 anyway.
     *
     * The returned view is the server's own copy: it silently drops [config]
     * values it does not recognise, so its echo — not the config sent — is what
     * callers must render.
     */
    suspend fun createView(
        listId: String,
        name: String,
        scope: ListViewScope?,
        config: ListViewConfig? = null,
        isDefault: Boolean = false,
    ): ApiResult<ListView>

    /**
     * Renames / re-configures a view or makes it the default. Only the supplied
     * fields change, and the server's echo is returned for the same reason as
     * [createView].
     */
    suspend fun updateView(
        listId: String,
        viewId: String,
        name: String? = null,
        config: ListViewConfig? = null,
        isDefault: Boolean? = null,
    ): ApiResult<ListView>

    /**
     * Forks a view into a personal copy named `"<name> (copy)"` — the escape
     * hatch when somebody else's shared view does not suit. The original is
     * untouched.
     */
    suspend fun forkView(listId: String, viewId: String): ApiResult<ListView>

    /** Deletes a saved view. The server rejects views the caller does not own. */
    suspend fun deleteView(listId: String, viewId: String): ApiResult<Unit>

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

    // --- Email invites -----------------------------------------------------

    /**
     * Pending email invites for a list. Free for any owner — a lapsed subscription
     * must never hide invites the owner still needs to revoke.
     */
    suspend fun getInvites(listId: String): ApiResult<List<ListInvite>>

    /**
     * Invites [email] at [role]. Refuses locally — issuing no request at all — when
     * the address is not a valid one, or when the signed-in account is known not to
     * be a subscriber (sending is a subscriber feature), in which case the failure is
     * [com.interlinedlist.android.core.common.result.AppError.SubscriptionRequired].
     */
    suspend fun sendInvite(
        listId: String,
        email: String,
        role: InviteRole,
    ): ApiResult<ListInvite>

    /** Revokes a pending invite, killing its link immediately. Never subscriber-gated. */
    suspend fun revokeInvite(listId: String, token: String): ApiResult<Unit>

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}
