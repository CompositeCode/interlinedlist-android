package com.interlinedlist.android.feature.ai.domain

import com.interlinedlist.android.feature.ai.data.AiRepository

/** Records what the AI surfaces asked for and replays canned outcomes. */
internal class FakeAiRepository : AiRepository {

    var availability: AiAvailability = AiAvailability.Available(AiQuota(0, 50, 50))
    var suggestResult: AiResult<AiPreview> = AiResult.Failure(AiError.Unknown("not stubbed"))
    var generateResult: AiResult<AiGeneration> =
        AiResult.Success(AiGeneration(null, AiCreated.Unrecognised))

    var availabilityCalls = 0
    var suggestCalls = mutableListOf<Pair<AiFeature, AiSuggestInput>>()
    var generateCalls = mutableListOf<ConfirmedPreview>()

    override suspend fun availability(): AiAvailability {
        availabilityCalls++
        return availability
    }

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
