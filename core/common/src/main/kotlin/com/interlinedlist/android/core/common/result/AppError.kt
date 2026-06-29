package com.interlinedlist.android.core.common.result

/**
 * Normalised error mapped from HTTP status codes and the API's
 * `{ "error": ... }` response body. [SubscriptionRequired] distinguishes the
 * 403 returned when a free user attempts a subscriber-only feature, so the UI
 * can show an upsell rather than a generic error.
 */
sealed class AppError(open val message: String?) {
    data class Network(override val message: String?) : AppError(message)
    data class Unauthorized(override val message: String?) : AppError(message)
    data class Forbidden(override val message: String?) : AppError(message)
    data class SubscriptionRequired(override val message: String?) : AppError(message)
    data class NotFound(override val message: String?) : AppError(message)
    data class Conflict(override val message: String?) : AppError(message)
    data class RateLimited(override val message: String?) : AppError(message)
    data class Server(override val message: String?) : AppError(message)
    data class Unknown(override val message: String?) : AppError(message)
}
