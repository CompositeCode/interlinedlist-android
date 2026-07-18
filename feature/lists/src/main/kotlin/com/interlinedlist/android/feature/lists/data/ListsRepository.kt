package com.interlinedlist.android.feature.lists.data

import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.lists.domain.ListDetail
import com.interlinedlist.android.feature.lists.domain.ListFolder
import com.interlinedlist.android.feature.lists.domain.ListRow
import com.interlinedlist.android.feature.lists.domain.ListSummary
import com.interlinedlist.android.feature.lists.domain.Paged
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

    /** Deletes a list and evicts it from the cache. */
    suspend fun deleteList(id: String): ApiResult<Unit>

    /** Loads a list's metadata, schema, and first page of rows for the detail screen. */
    suspend fun getListDetail(id: String, rowLimit: Int = DEFAULT_PAGE_SIZE): ApiResult<ListDetail>

    suspend fun addRow(listId: String, values: Map<String, String>): ApiResult<ListRow>

    suspend fun updateRow(listId: String, rowId: String, values: Map<String, String>): ApiResult<ListRow>

    suspend fun deleteRow(listId: String, rowId: String): ApiResult<Unit>

    suspend fun getFolders(): ApiResult<List<ListFolder>>

    suspend fun createFolder(name: String, parentId: String?): ApiResult<ListFolder>

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}
