package com.interlinedlist.android.feature.lists

import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.lists.data.ListsRepository
import com.interlinedlist.android.feature.lists.domain.ListDetail
import com.interlinedlist.android.feature.lists.domain.ListFolder
import com.interlinedlist.android.feature.lists.domain.ListRow
import com.interlinedlist.android.feature.lists.domain.ListSchema
import com.interlinedlist.android.feature.lists.domain.ListSummary
import com.interlinedlist.android.feature.lists.domain.Paged
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
    var deleteResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var detailResult: ApiResult<ListDetail>? = null
    var addRowResult: ApiResult<ListRow>? = null
    var updateRowResult: ApiResult<ListRow>? = null
    var deleteRowResult: ApiResult<Unit> = ApiResult.Success(Unit)

    var refreshCount = 0
    var loadMoreCount = 0

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

    override suspend fun addRow(listId: String, values: Map<String, String>): ApiResult<ListRow> =
        addRowResult ?: ApiResult.Success(ListRow("row-new", values))

    override suspend fun updateRow(listId: String, rowId: String, values: Map<String, String>): ApiResult<ListRow> =
        updateRowResult ?: ApiResult.Success(ListRow(rowId, values))

    override suspend fun deleteRow(listId: String, rowId: String): ApiResult<Unit> = deleteRowResult

    override suspend fun getFolders(): ApiResult<List<ListFolder>> = ApiResult.Success(emptyList())

    override suspend fun createFolder(name: String, parentId: String?): ApiResult<ListFolder> =
        ApiResult.Success(ListFolder("f", name, parentId))

    companion object {
        fun subscriptionFailure(): ApiResult.Failure =
            ApiResult.Failure(AppError.SubscriptionRequired("Lists require an active subscription"))
    }
}
