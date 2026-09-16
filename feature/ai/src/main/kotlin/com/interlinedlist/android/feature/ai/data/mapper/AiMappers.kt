package com.interlinedlist.android.feature.ai.data.mapper

import com.interlinedlist.android.feature.ai.data.remote.dto.AiCreatedDto
import com.interlinedlist.android.feature.ai.data.remote.dto.AiGenerateResponse
import com.interlinedlist.android.feature.ai.data.remote.dto.AiQuotaDto
import com.interlinedlist.android.feature.ai.data.remote.dto.AiStatusDto
import com.interlinedlist.android.feature.ai.data.remote.dto.AiSuggestResponse
import com.interlinedlist.android.feature.ai.data.remote.dto.AiUsageDto
import com.interlinedlist.android.feature.ai.domain.AiArtifact
import com.interlinedlist.android.feature.ai.domain.AiAvailability
import com.interlinedlist.android.feature.ai.domain.AiCreated
import com.interlinedlist.android.feature.ai.domain.AiFeature
import com.interlinedlist.android.feature.ai.domain.AiGeneration
import com.interlinedlist.android.feature.ai.domain.AiPreview
import com.interlinedlist.android.feature.ai.domain.AiQuota
import com.interlinedlist.android.feature.ai.domain.AiUsage
import kotlinx.serialization.json.JsonObject

internal fun AiQuotaDto.toDomain(): AiQuota = AiQuota(
    usedToday = usedToday,
    dailyLimit = dailyLimit,
    remaining = remaining,
)

internal fun AiUsageDto.toDomain(): AiUsage = AiUsage(
    inputTokens = inputTokens,
    outputTokens = outputTokens,
    model = model,
)

/**
 * Resolves `/api/ai/status` into the gate state.
 *
 * [subscriber] is the caller's resolved subscription flag — the body's own
 * `subscriber` when it sent one, otherwise the account's `customerStatus`, and
 * null when neither could be read.
 *
 * Precedence: an explicitly empty `providers` array means the deployment has no
 * AI key at all and hides AI for everyone, subscriber or not. An *absent*
 * `providers` field is not the same claim, so it does not hide anything. A
 * subscription that cannot be confirmed hides AI too — a free account must
 * never see an AI control.
 */
internal fun AiStatusDto.toAvailability(subscriber: Boolean?): AiAvailability = when {
    providers?.isEmpty() == true -> AiAvailability.Unavailable
    subscriber == null -> AiAvailability.Unavailable
    !subscriber -> AiAvailability.NotSubscribed
    else -> AiAvailability.Available(quota?.toDomain())
}

/** Builds the preview, defaulting the feature to the one that was requested. */
internal fun AiSuggestResponse.toDomain(requested: AiFeature): AiPreview = AiPreview(
    feature = AiFeature.fromApiValue(feature) ?: requested,
    artifact = AiArtifact(artifact ?: JsonObject(emptyMap())),
    usage = usage?.toDomain(),
    quota = quota?.toDomain(),
)

internal fun AiGenerateResponse.toDomain(requested: AiFeature): AiGeneration = AiGeneration(
    feature = AiFeature.fromApiValue(feature) ?: requested,
    created = created?.toDomain() ?: AiCreated.Unrecognised,
    quota = quota?.toDomain(),
)

/**
 * Picks the one populated group. Scheduled posts and a document folder are
 * checked first because those responses also carry the ids of what they wrap.
 */
internal fun AiCreatedDto.toDomain(): AiCreated = when {
    !scheduledMessageIds.isNullOrEmpty() -> AiCreated.ScheduledMessagesCreated(
        messageIds = scheduledMessageIds,
        firstScheduledAt = firstScheduledAt,
        lastScheduledAt = lastScheduledAt,
    )
    folderId != null -> AiCreated.DocumentSeriesCreated(
        folderId = folderId,
        documentIds = documentIds.orEmpty(),
    )
    listId != null -> AiCreated.ListCreated(listId)
    documentId != null -> AiCreated.DocumentCreated(documentId)
    else -> AiCreated.Unrecognised
}
