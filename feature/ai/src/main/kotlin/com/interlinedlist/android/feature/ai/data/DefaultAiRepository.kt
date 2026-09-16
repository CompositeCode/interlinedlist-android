package com.interlinedlist.android.feature.ai.data

import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.network.api.InterlinedListApi
import com.interlinedlist.android.core.network.dto.toDomain
import com.interlinedlist.android.core.network.error.safeApiCall
import com.interlinedlist.android.feature.ai.data.mapper.toAvailability
import com.interlinedlist.android.feature.ai.data.mapper.toDomain
import com.interlinedlist.android.feature.ai.data.remote.AiApi
import com.interlinedlist.android.feature.ai.data.remote.dto.AiGenerateRequest
import com.interlinedlist.android.feature.ai.data.remote.dto.AiSuggestRequest
import com.interlinedlist.android.feature.ai.domain.AiAvailability
import com.interlinedlist.android.feature.ai.domain.AiFeature
import com.interlinedlist.android.feature.ai.domain.AiGenerateOptions
import com.interlinedlist.android.feature.ai.domain.AiGeneration
import com.interlinedlist.android.feature.ai.domain.AiPreview
import com.interlinedlist.android.feature.ai.domain.AiResult
import com.interlinedlist.android.feature.ai.domain.AiSuggestInput
import com.interlinedlist.android.feature.ai.domain.ConfirmedPreview
import com.interlinedlist.android.feature.ai.domain.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject

class DefaultAiRepository @Inject constructor(
    private val api: AiApi,
    /** Shared current-user endpoint, used only for the `customerStatus` fallback. */
    private val userApi: InterlinedListApi,
    private val json: Json,
    private val dispatchers: DispatcherProvider,
) : AiRepository {

    override suspend fun availability(): AiAvailability = withContext(dispatchers.io) {
        when (val status = aiApiCall(json) { api.getStatus() }) {
            // Status unreadable (offline, 401, unexpected body) — hide AI rather
            // than offering a control whose action would fail.
            is AiResult.Failure -> AiAvailability.Unavailable
            is AiResult.Success ->
                status.data.toAvailability(status.data.subscriber ?: currentUserIsSubscriber())
        }
    }

    override suspend fun suggest(
        feature: AiFeature,
        input: AiSuggestInput,
    ): AiResult<AiPreview> = withContext(dispatchers.io) {
        val request = AiSuggestRequest(
            feature = feature.apiValue,
            input = input.input,
            context = input.context,
            model = input.model,
            maxOutputTokens = input.maxOutputTokens,
        )
        aiApiCall(json) { api.suggest(request) }.map { it.toDomain(feature) }
    }

    override suspend fun generate(
        confirmed: ConfirmedPreview,
        options: AiGenerateOptions,
    ): AiResult<AiGeneration> = withContext(dispatchers.io) {
        val request = AiGenerateRequest(
            feature = confirmed.feature.apiValue,
            // Sent back exactly as confirmed; the server re-validates it.
            artifact = confirmed.artifact.payload,
            model = options.model,
            scheduleImmediately = options.scheduleImmediately,
            crossPost = options.crossPost,
        )
        aiApiCall(json) { api.generate(request) }.map { it.toDomain(confirmed.feature) }
    }

    /**
     * `/api/ai/status` reports `subscriber` itself; this covers a deployment that
     * omits it, reusing the same `customerStatus` the rest of the app gates on.
     * Null means the subscription could not be established at all.
     */
    private suspend fun currentUserIsSubscriber(): Boolean? =
        when (val result = safeApiCall(json) { userApi.getCurrentUser().user }) {
            is ApiResult.Success -> result.data.toDomain().customerStatus.isSubscriber
            is ApiResult.Failure -> null
        }
}
