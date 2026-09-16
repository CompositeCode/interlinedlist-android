package com.interlinedlist.android.feature.ai.domain

/**
 * Whether the AI affordances may be shown at all. Resolved from
 * `GET /api/ai/status` (see `AiRepository.availability`) and observed through
 * [AiGate].
 *
 * Only [Available] permits an AI control to be drawn: a free account, an
 * account whose subscription could not be confirmed, and a deployment with no
 * AI provider configured all hide the AI surfaces entirely rather than offering
 * a control that would fail.
 */
sealed interface AiAvailability {

    /** Status has not been read yet. Treated as "hide" until [AiGate.refresh] resolves it. */
    data object Unknown : AiAvailability

    /**
     * AI cannot be offered: the deployment has no provider configured, or the
     * status/subscription could not be read at all.
     */
    data object Unavailable : AiAvailability

    /** The provider is configured but this account is not a subscriber. */
    data object NotSubscribed : AiAvailability

    /** AI may be used. [quota] is null when the server did not report one. */
    data class Available(val quota: AiQuota? = null) : AiAvailability

    /** The single check an AI surface makes before drawing anything. */
    val isEnabled: Boolean
        get() = this is Available

    /** True when AI is enabled but today's 50-action allowance is spent. */
    val isQuotaExhausted: Boolean
        get() = this is Available && quota?.isExhausted == true
}
