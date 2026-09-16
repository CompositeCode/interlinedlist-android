package com.interlinedlist.android.core.materialize.data.remote

import com.interlinedlist.android.core.materialize.data.remote.dto.MaterializeRequestDto
import com.interlinedlist.android.core.materialize.data.remote.dto.MaterializeResponseDto
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Retrofit description of `POST /api/materialize`, built from the shared authed
 * Retrofit (base URL and `Authorization: Bearer …` already applied).
 *
 * One endpoint backs all four destinations; the `target` in the body chooses.
 */
interface MaterializeApi {

    /**
     * Creates a list, a document, or both from an id-only source — or, for
     * `target: "message"`, returns a draft and creates nothing.
     */
    @POST("api/materialize")
    suspend fun materialize(@Body request: MaterializeRequestDto): MaterializeResponseDto
}
