package com.interlinedlist.android.feature.integrations.domain

/**
 * The plan/usage summary from `GET /api/limits`. The live shape isn't pinned in
 * the OpenAPI spec, so the repository maps whatever it recognises into this flat
 * list of named limits; unknown fields are ignored rather than failing the read.
 */
data class PlanLimits(
    val planName: String?,
    val limits: List<Limit>,
) {
    /**
     * One metered resource. [max] is null when the plan is unlimited for it;
     * [used] is null when the API doesn't report current usage.
     */
    data class Limit(
        val key: String,
        val label: String,
        val used: Int?,
        val max: Int?,
    ) {
        val isUnlimited: Boolean get() = max == null
    }
}
