package com.interlinedlist.android.feature.integrations.data.remote

import com.interlinedlist.android.feature.integrations.data.remote.dto.ConnectionStatusDto
import com.interlinedlist.android.feature.integrations.data.remote.dto.LimitsDto
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Streaming

/**
 * Retrofit description of the Phase 8 integrations endpoints. The shared Retrofit
 * instance already carries the base URL and Bearer token, so these calls are
 * authed.
 *
 * Exports return raw CSV, so they surface as a [Streaming] [ResponseBody] rather
 * than a deserialised type — the repository copies the bytes straight to disk
 * without buffering the whole file in memory.
 */
interface IntegrationsApi {

    /** CSV of the user's data for the given export type (path segment from ExportType). */
    @Streaming
    @GET("api/exports/{type}")
    suspend fun downloadExport(@Path("type") type: String): ResponseBody

    /** Connection status for one provider, e.g. `api/auth/github/status`. */
    @GET("{statusPath}")
    suspend fun getConnectionStatus(
        @Path(value = "statusPath", encoded = true) statusPath: String,
    ): ConnectionStatusDto

    /** Plan limits/usage for the current user. */
    @GET("api/limits")
    suspend fun getLimits(): LimitsDto
}
