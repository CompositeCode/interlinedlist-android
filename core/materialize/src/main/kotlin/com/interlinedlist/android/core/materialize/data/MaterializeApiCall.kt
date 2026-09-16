package com.interlinedlist.android.core.materialize.data

import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.core.materialize.data.remote.dto.MaterializeErrorCode
import com.interlinedlist.android.core.materialize.data.remote.dto.MaterializeErrorDto
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

/**
 * Runs the materialize call and normalises every failure onto the shared
 * [AppError], so the feature modules that open this flow keep using the
 * `toUserMessage()` / `isSubscriptionGate` helpers they already have.
 *
 * This is not `safeApiCall`: that helper drops `code` and decides the
 * subscription case by looking for the word "subscription" in the message. This
 * endpoint is documented as subscriber-only, so **its 403 is the subscriber
 * gate** whatever the wording — except when the code says the account itself is
 * restricted, suspended or on probation, which no subscription would fix and
 * which must therefore not become an upsell.
 */
internal suspend fun <T> materializeApiCall(
    json: Json,
    block: suspend () -> T,
): ApiResult<T> = try {
    ApiResult.Success(block())
} catch (e: HttpException) {
    ApiResult.Failure(e.toAppError(json))
} catch (e: IOException) {
    ApiResult.Failure(AppError.Network(e.message))
} catch (e: Exception) {
    ApiResult.Failure(AppError.Unknown(e.message))
}

private fun HttpException.toAppError(json: Json): AppError {
    val body = runCatching { response()?.errorBody()?.string() }.getOrNull()
    val dto = body
        ?.takeIf { it.isNotBlank() }
        ?.let { runCatching { json.decodeFromString(MaterializeErrorDto.serializer(), it) }.getOrNull() }
    val message = dto?.error
    val code = dto?.code

    return when {
        // An account-status refusal is a 403 a subscription would not lift.
        code?.startsWith(MaterializeErrorCode.ACCOUNT_PREFIX) == true -> AppError.Forbidden(message)
        code == MaterializeErrorCode.SUBSCRIPTION_REQUIRED -> AppError.SubscriptionRequired(message)
        code == MaterializeErrorCode.UNAUTHORIZED -> AppError.Unauthorized(message)
        code == MaterializeErrorCode.NOT_FOUND -> AppError.NotFound(message)
        code == MaterializeErrorCode.RATE_LIMITED -> AppError.RateLimited(message)
        // `bad_request` / `validation_failed` carry a message worth showing
        // verbatim ("Missing source", "Field 'year' has invalid type …"); the
        // shared error type has no validation case, and `Unknown` renders the
        // server's own words in every feature's `toUserMessage()`.
        code == MaterializeErrorCode.BAD_REQUEST ||
            code == MaterializeErrorCode.VALIDATION_FAILED -> AppError.Unknown(message)
        code == MaterializeErrorCode.INTERNAL_ERROR -> AppError.Server(message)
        // `code` is optional on the wire — fall back to the status.
        else -> fromStatus(code(), message)
    }
}

/** Fallback for a response that carried no `code`. */
private fun fromStatus(status: Int, message: String?): AppError = when (status) {
    401 -> AppError.Unauthorized(message)
    // The only 403 this endpoint documents is the subscriber gate.
    403 -> AppError.SubscriptionRequired(message)
    // "A referenced id is not found or not owned by you."
    404 -> AppError.NotFound(message)
    409 -> AppError.Conflict(message)
    429 -> AppError.RateLimited(message)
    in 500..599 -> AppError.Server(message)
    // Includes the documented 400: show the server's own explanation.
    else -> AppError.Unknown(message ?: "HTTP $status")
}
