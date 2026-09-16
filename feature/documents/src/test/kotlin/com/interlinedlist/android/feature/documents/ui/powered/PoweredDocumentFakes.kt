package com.interlinedlist.android.feature.documents.ui.powered

import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.ai.data.AiRepository
import com.interlinedlist.android.feature.ai.domain.AiAvailability
import com.interlinedlist.android.feature.ai.domain.AiCreated
import com.interlinedlist.android.feature.ai.domain.AiError
import com.interlinedlist.android.feature.ai.domain.AiFeature
import com.interlinedlist.android.feature.ai.domain.AiGenerateOptions
import com.interlinedlist.android.feature.ai.domain.AiGeneration
import com.interlinedlist.android.feature.ai.domain.AiPreview
import com.interlinedlist.android.feature.ai.domain.AiQuota
import com.interlinedlist.android.feature.ai.domain.AiResult
import com.interlinedlist.android.feature.ai.domain.AiSuggestInput
import com.interlinedlist.android.feature.ai.domain.ConfirmedPreview
import com.interlinedlist.android.feature.documents.data.ListSourcesRepository
import com.interlinedlist.android.feature.documents.domain.ListSource

/**
 * Records every `/suggest` and `/generate` the surface issues, so a test can
 * assert that backing out of a preview writes nothing at all.
 */
class FakeAiRepository : AiRepository {

    var availability: AiAvailability = AiAvailability.Available(AiQuota(0, 50, 50))
    var suggestResult: AiResult<AiPreview> = AiResult.Failure(AiError.Unknown("not stubbed"))
    var generateResult: AiResult<AiGeneration> =
        AiResult.Success(AiGeneration(AiFeature.POWERED_DOCUMENT, AiCreated.DocumentCreated("doc-new")))

    val suggestCalls = mutableListOf<Pair<AiFeature, AiSuggestInput>>()
    val generateCalls = mutableListOf<ConfirmedPreview>()

    override suspend fun availability(): AiAvailability = availability

    override suspend fun suggest(feature: AiFeature, input: AiSuggestInput): AiResult<AiPreview> {
        suggestCalls += feature to input
        return suggestResult
    }

    override suspend fun generate(
        confirmed: ConfirmedPreview,
        options: AiGenerateOptions,
    ): AiResult<AiGeneration> {
        generateCalls += confirmed
        return generateResult
    }
}

/** Replays a canned `/api/lists` page for the list-source picker. */
class FakeListSourcesRepository : ListSourcesRepository {

    var result: ApiResult<List<ListSource>> = ApiResult.Success(emptyList())
    var calls = 0

    override suspend fun getListSources(): ApiResult<List<ListSource>> {
        calls++
        return result
    }

    fun failWith(error: AppError) {
        result = ApiResult.Failure(error)
    }
}
