package com.interlinedlist.android.feature.ai.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * `POST /api/ai/generate` body: the same `feature` discriminator plus the
 * confirmed artifact from `/suggest`. `scheduleImmediately` and `crossPost` are
 * honoured for `message_series` only; `model` is recorded for auditing.
 */
@Serializable
data class AiGenerateRequest(
    val feature: String,
    val artifact: JsonObject,
    val model: String? = null,
    val scheduleImmediately: Boolean? = null,
    val crossPost: JsonObject? = null,
)

/**
 * `201 Created` from `/generate`:
 * `{ "ok": true, "feature": "powered_document", "created": { … }, "quota": { … } }`.
 */
@Serializable
data class AiGenerateResponse(
    val ok: Boolean? = null,
    val feature: String? = null,
    val created: AiCreatedDto? = null,
    val quota: AiQuotaDto? = null,
)

/**
 * The union of the four documented `created` shapes: `{ listId }`,
 * `{ documentId }`, `{ folderId, documentIds }`, and
 * `{ scheduledMessageIds, firstScheduledAt, lastScheduledAt }`. Only one group
 * is populated per response.
 */
@Serializable
data class AiCreatedDto(
    val listId: String? = null,
    val documentId: String? = null,
    val folderId: String? = null,
    val documentIds: List<String>? = null,
    val scheduledMessageIds: List<String>? = null,
    val firstScheduledAt: String? = null,
    val lastScheduledAt: String? = null,
)
