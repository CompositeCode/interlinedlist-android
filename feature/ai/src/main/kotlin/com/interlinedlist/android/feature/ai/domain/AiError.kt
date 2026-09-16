package com.interlinedlist.android.feature.ai.domain

/**
 * A failure from an AI endpoint, keyed off the `code` in the API's
 * `{ "error": "…", "code": "…" }` envelope and falling back to the HTTP status
 * when the route did not send one.
 *
 * The AI routes distinguish cases the shared `AppError` cannot express — a
 * deployment with no provider key (409 `no_provider_configured`) versus the
 * daily quota being spent (429 `quota_exceeded`) versus the short-window rate
 * limit (429 `rate_limited`) — and the UI has to say something different for
 * each, so this module carries its own error type.
 */
sealed interface AiError {

    val message: String?

    /** 401 `unauthorized` — no valid session or bearer token. */
    data class NotAuthenticated(override val message: String? = null) : AiError

    /** 403 `subscription_required` — authenticated, but not a subscriber. */
    data class NotSubscribed(override val message: String? = null) : AiError

    /** 403 for a reason other than the subscription (restricted/suspended/probation account). */
    data class Forbidden(override val message: String? = null) : AiError

    /** 409 `no_provider_configured` — server-side misconfiguration; hide the AI surfaces. */
    data class ProviderUnconfigured(override val message: String? = null) : AiError

    /** 429 `quota_exceeded` — the 50-per-day allowance is spent. */
    data class QuotaExceeded(override val message: String? = null) : AiError

    /** 429 `rate_limited` — 15 requests/60s tripped; honour [retryAfterSeconds]. */
    data class RateLimited(
        override val message: String? = null,
        val retryAfterSeconds: Int? = null,
    ) : AiError

    /** 422 `invalid_input` — empty/over-length input, under the 10-word series gate, bad reference. */
    data class InvalidInput(override val message: String? = null) : AiError

    /** 422 `invalid_ai_output` / `refused` — the model's output failed validation. */
    data class InvalidOutput(override val message: String? = null) : AiError

    /** 500 / 502 `provider_error` — upstream failure or timeout. */
    data class ProviderFailure(override val message: String? = null) : AiError

    /** No usable connection. */
    data class Network(override val message: String? = null) : AiError

    /** Anything not recognised. */
    data class Unknown(override val message: String? = null) : AiError
}

/**
 * Outcome of an AI call. Mirrors `ApiResult` from `:core:common` but carries the
 * AI-specific [AiError] instead of the shared `AppError`.
 */
sealed interface AiResult<out T> {
    data class Success<T>(val data: T) : AiResult<T>
    data class Failure(val error: AiError) : AiResult<Nothing>
}

/** Transforms the success value, propagating failures unchanged. */
inline fun <T, R> AiResult<T>.map(transform: (T) -> R): AiResult<R> = when (this) {
    is AiResult.Success -> AiResult.Success(transform(data))
    is AiResult.Failure -> this
}
