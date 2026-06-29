package com.interlinedlist.android.core.network.error

import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.core.network.dto.ErrorDto
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

/**
 * Runs a suspending API call and normalises failures into [ApiResult].
 *
 * HTTP errors map to typed [AppError]s using the status code and the API's
 * `{ "error": ... }` body; a 403 whose message mentions a subscription becomes
 * [AppError.SubscriptionRequired] so the UI can show an upsell instead of a
 * generic error. I/O failures map to [AppError.Network].
 */
suspend fun <T> safeApiCall(json: Json, block: suspend () -> T): ApiResult<T> = try {
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
    val message = body
        ?.takeIf { it.isNotBlank() }
        ?.let { runCatching { json.decodeFromString(ErrorDto.serializer(), it).error }.getOrNull() }
    return when (val code = code()) {
        401 -> AppError.Unauthorized(message)
        403 -> if (message?.contains("subscription", ignoreCase = true) == true) {
            AppError.SubscriptionRequired(message)
        } else {
            AppError.Forbidden(message)
        }
        404 -> AppError.NotFound(message)
        409 -> AppError.Conflict(message)
        429 -> AppError.RateLimited(message)
        in 500..599 -> AppError.Server(message)
        else -> AppError.Unknown(message ?: "HTTP $code")
    }
}
