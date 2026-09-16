package com.interlinedlist.android.feature.ai.data

import com.interlinedlist.android.feature.ai.domain.AiAvailability
import com.interlinedlist.android.feature.ai.domain.AiFeature
import com.interlinedlist.android.feature.ai.domain.AiGenerateOptions
import com.interlinedlist.android.feature.ai.domain.AiGeneration
import com.interlinedlist.android.feature.ai.domain.AiPreview
import com.interlinedlist.android.feature.ai.domain.AiResult
import com.interlinedlist.android.feature.ai.domain.AiSuggestInput
import com.interlinedlist.android.feature.ai.domain.ConfirmedPreview

/**
 * The three `/api/ai/…` endpoints as one flow. Everything here is a live read or
 * write — nothing about an AI action is worth caching, and a stale quota would
 * be actively misleading.
 *
 * AI surfaces should not call [suggest]/[generate] directly unless they already
 * know AI is enabled; `AiGate` is the gate for that.
 */
interface AiRepository {

    /**
     * Reads `GET /api/ai/status` and resolves whether AI may be offered at all.
     * Never fails: anything unreadable resolves to [AiAvailability.Unavailable]
     * so the AI surfaces hide instead of erroring.
     */
    suspend fun availability(): AiAvailability

    /**
     * Runs [feature] against [input] and returns a **preview**. Writes nothing —
     * the returned [AiPreview] must be confirmed by the user before [generate]
     * can be reached. Counts against the daily quota.
     */
    suspend fun suggest(feature: AiFeature, input: AiSuggestInput): AiResult<AiPreview>

    /**
     * Persists a preview the user confirmed. Takes a [ConfirmedPreview] rather
     * than a feature + artifact so an unapproved suggestion cannot be written;
     * the discriminator comes from the preview itself and cannot drift.
     * Counts against the daily quota as a second action.
     */
    suspend fun generate(
        confirmed: ConfirmedPreview,
        options: AiGenerateOptions = AiGenerateOptions(),
    ): AiResult<AiGeneration>
}
