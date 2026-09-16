package com.interlinedlist.android.feature.documents.data

import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.map
import com.interlinedlist.android.core.network.error.safeApiCall
import com.interlinedlist.android.feature.documents.data.remote.ListSourcesApi
import com.interlinedlist.android.feature.documents.domain.ListSource
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Read-only access to the user's lists, used solely to populate the "Derived
 * From List" source picker. Nothing is cached: the picker is opened rarely and a
 * list created moments ago must show up.
 */
interface ListSourcesRepository {

    /** The user's lists as pickable sources. */
    suspend fun getListSources(): ApiResult<List<ListSource>>
}

class DefaultListSourcesRepository @Inject constructor(
    private val api: ListSourcesApi,
    private val json: Json,
    private val dispatchers: DispatcherProvider,
) : ListSourcesRepository {

    override suspend fun getListSources(): ApiResult<List<ListSource>> =
        withContext(dispatchers.io) {
            safeApiCall(json) { api.getLists(PICKER_LIMIT) }.map { response ->
                response.listsOrEmpty.mapNotNull { dto ->
                    val id = dto.id?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    ListSource(
                        id = id,
                        title = dto.title?.takeIf { it.isNotBlank() } ?: "Untitled list",
                        description = dto.description?.takeIf { it.isNotBlank() },
                    )
                }
            }
        }

    private companion object {
        /** A picker, not a browser — one page of lists is plenty. */
        const val PICKER_LIMIT = 100
    }
}
