package com.interlinedlist.android.feature.ai.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * The AI routes' error envelope — `{ "error": "human message", "code": "machine_code" }`.
 * The shared `ErrorDto` in `:core:network` drops `code`, and the AI surfaces have
 * to tell `quota_exceeded` from `rate_limited` (both 429) and
 * `no_provider_configured` from any other conflict, so this module reads it.
 */
@Serializable
data class AiErrorDto(
    val error: String? = null,
    val code: String? = null,
)

/** The machine-readable codes documented for `/api/ai/…`. */
internal object AiErrorCode {
    const val UNAUTHORIZED = "unauthorized"
    const val SUBSCRIPTION_REQUIRED = "subscription_required"
    const val NO_PROVIDER_CONFIGURED = "no_provider_configured"
    const val QUOTA_EXCEEDED = "quota_exceeded"
    const val RATE_LIMITED = "rate_limited"
    const val INVALID_INPUT = "invalid_input"
    const val INVALID_AI_OUTPUT = "invalid_ai_output"
    const val REFUSED = "refused"
    const val PROVIDER_ERROR = "provider_error"

    /** Account-status codes (`account_restricted`, `account_suspended`, …) share this prefix. */
    const val ACCOUNT_PREFIX = "account_"
}
