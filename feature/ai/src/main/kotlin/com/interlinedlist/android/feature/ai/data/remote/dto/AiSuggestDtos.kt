package com.interlinedlist.android.feature.ai.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * `POST /api/ai/suggest` body. `feature` and `input` are required; the rest are
 * omitted when null (the shared Json is configured with `explicitNulls = false`).
 */
@Serializable
data class AiSuggestRequest(
    val feature: String,
    val input: String,
    val context: JsonObject? = null,
    val model: String? = null,
    val maxOutputTokens: Int? = null,
)

/**
 * `200 OK` from `/suggest`:
 * ```
 * { "ok": true, "feature": "writing_assist",
 *   "artifact": { "kind": "message", "content": "…" },
 *   "usage": { "inputTokens": 412, "outputTokens": 96, "model": "claude-sonnet-5" },
 *   "quota": { "usedToday": 7, "dailyLimit": 50 } }
 * ```
 * The artifact stays a raw [JsonObject]: its shape depends on the feature and it
 * has to be handed back to `/generate` byte-for-byte.
 */
@Serializable
data class AiSuggestResponse(
    val ok: Boolean? = null,
    val feature: String? = null,
    val artifact: JsonObject? = null,
    val usage: AiUsageDto? = null,
    val quota: AiQuotaDto? = null,
)

@Serializable
data class AiUsageDto(
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val model: String? = null,
)
