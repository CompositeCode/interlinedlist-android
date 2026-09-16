package com.interlinedlist.android.feature.documents.data.remote

import com.interlinedlist.android.feature.documents.data.remote.dto.ListSourcesResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * The one lists endpoint this module needs: the picker behind the "Derived From
 * List" Powered Document mode.
 *
 * It is declared here, and not imported from `:feature:lists`, because no feature
 * module in this repo depends on another. Each feature owns its own Retrofit
 * interface over the shared authed Retrofit, so reading `/api/lists` from the
 * documents module costs one read-only call and keeps the module graph flat.
 */
interface ListSourcesApi {

    /** The user's lists, newest first, capped at [limit]. */
    @GET("api/lists")
    suspend fun getLists(@Query("limit") limit: Int): ListSourcesResponse
}
