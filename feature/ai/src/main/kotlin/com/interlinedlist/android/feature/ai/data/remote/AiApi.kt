package com.interlinedlist.android.feature.ai.data.remote

import com.interlinedlist.android.feature.ai.data.remote.dto.AiGenerateRequest
import com.interlinedlist.android.feature.ai.data.remote.dto.AiGenerateResponse
import com.interlinedlist.android.feature.ai.data.remote.dto.AiStatusDto
import com.interlinedlist.android.feature.ai.data.remote.dto.AiSuggestRequest
import com.interlinedlist.android.feature.ai.data.remote.dto.AiSuggestResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Retrofit description of `/api/ai/…`, built from the shared authed Retrofit
 * (base URL and `Authorization: Bearer …` are already applied).
 *
 * The three endpoints are one flow: [getStatus] decides whether AI is offered at
 * all, [suggest] returns a preview and writes nothing, and [generate] persists a
 * preview the user confirmed.
 */
interface AiApi {

    /** Configured providers, the caller's subscriber flag, and the daily quota. */
    @GET("api/ai/status")
    suspend fun getStatus(): AiStatusDto

    /** Runs a feature and returns a validated preview artifact. Writes nothing. */
    @POST("api/ai/suggest")
    suspend fun suggest(@Body request: AiSuggestRequest): AiSuggestResponse

    /** Persists a confirmed artifact and returns the created resource id(s). */
    @POST("api/ai/generate")
    suspend fun generate(@Body request: AiGenerateRequest): AiGenerateResponse
}
