package com.interlinedlist.android.feature.integrations.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The `/api/limits` payload. Its exact shape isn't pinned in the spec, so this
 * models the common `{ plan, limits: { <key>: { used, max } } }` form. `limits`
 * is a map keyed by resource name; unknown keys survive because each value is a
 * lenient [LimitEntryDto]. Both a flat `{ key: max }` and a `{ used, limit }`
 * object form are tolerated by the mapper.
 */
@Serializable
data class LimitsDto(
    val plan: String? = null,
    @SerialName("planName") val planName: String? = null,
    val limits: Map<String, LimitEntryDto>? = null,
)

@Serializable
data class LimitEntryDto(
    val used: Int? = null,
    val max: Int? = null,
    val limit: Int? = null,
) {
    /** Reconciles the two spellings the API might use for the ceiling. */
    val ceiling: Int? get() = max ?: limit
}
