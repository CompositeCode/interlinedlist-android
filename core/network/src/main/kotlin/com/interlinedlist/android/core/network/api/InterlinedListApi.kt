package com.interlinedlist.android.core.network.api

import com.interlinedlist.android.core.network.dto.CurrentUserResponse
import com.interlinedlist.android.core.network.dto.SyncTokenRequest
import com.interlinedlist.android.core.network.dto.SyncTokenResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Retrofit description of the InterlinedList REST API.
 *
 * Phase 0 covers the auth bootstrap and current-user endpoints. Subsequent
 * endpoints (lists, messages, documents, following, notifications, …) are added
 * in their respective feature phases — see docs/ROADMAP.md.
 */
interface InterlinedListApi {

    /** Exchanges credentials for a bearer token (`il_tok_...`). */
    @POST("api/auth/sync-token")
    suspend fun createSyncToken(@Body body: SyncTokenRequest): SyncTokenResponse

    /** Returns the authenticated user, wrapped as `{ "user": ... }`, including `customerStatus`. */
    @GET("api/user")
    suspend fun getCurrentUser(): CurrentUserResponse
}
