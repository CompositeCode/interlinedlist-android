package com.interlinedlist.android.feature.ai.domain

/**
 * The daily AI allowance reported by `/api/ai/status` (and echoed by `/suggest`
 * and `/generate`): 50 actions per rolling 24 hours per account, where a preview
 * and its confirmation each count as one.
 *
 * Every field is nullable because the server may omit the `quota` object or any
 * of its members; [remainingActions] reconciles the two ways the remainder can
 * be known.
 */
data class AiQuota(
    val usedToday: Int? = null,
    val dailyLimit: Int? = null,
    val remaining: Int? = null,
) {
    /** `remaining` when the server sends it, else `dailyLimit - usedToday`. */
    val remainingActions: Int?
        get() = remaining ?: dailyLimit?.let { limit -> usedToday?.let { used -> limit - used } }

    /** True only when the remainder is known to be spent — unknown never blocks the UI. */
    val isExhausted: Boolean
        get() = remainingActions?.let { it <= 0 } == true
}
