package com.interlinedlist.android.feature.ai.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * `GET /api/ai/status`:
 * ```
 * { "subscriber": true,
 *   "providers": ["anthropic"],
 *   "defaultModels": { "anthropic": "claude-sonnet-5" },
 *   "quota": { "usedToday": 8, "dailyLimit": 50, "remaining": 42 } }
 * ```
 * Bare object, no envelope. Every field is nullable so an older or trimmed
 * deployment cannot fail the read; the distinction between "absent" and
 * "empty list" matters for [providers] and is preserved.
 */
@Serializable
data class AiStatusDto(
    val subscriber: Boolean? = null,
    val providers: List<String>? = null,
    val defaultModels: Map<String, String>? = null,
    val quota: AiQuotaDto? = null,
)

/** The `quota` object, reported by `/status`, `/suggest` and `/generate` alike. */
@Serializable
data class AiQuotaDto(
    val usedToday: Int? = null,
    val dailyLimit: Int? = null,
    val remaining: Int? = null,
)
