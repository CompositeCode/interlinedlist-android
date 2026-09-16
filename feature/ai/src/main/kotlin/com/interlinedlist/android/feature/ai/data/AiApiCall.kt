package com.interlinedlist.android.feature.ai.data

import com.interlinedlist.android.feature.ai.data.remote.dto.AiErrorCode
import com.interlinedlist.android.feature.ai.data.remote.dto.AiErrorDto
import com.interlinedlist.android.feature.ai.domain.AiError
import com.interlinedlist.android.feature.ai.domain.AiResult
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

/**
 * Runs an AI call and normalises every failure into an [AiError].
 *
 * The AI routes always answer `{ "error": …, "code": … }`, so the `code` is the
 * primary key and the HTTP status is only the fallback for a route that omitted
 * it. This is the one place a `/api/ai/…` failure is interpreted.
 */
internal suspend fun <T> aiApiCall(json: Json, block: suspend () -> T): AiResult<T> = try {
    AiResult.Success(block())
} catch (e: HttpException) {
    AiResult.Failure(e.toAiError(json))
} catch (e: IOException) {
    AiResult.Failure(AiError.Network(e.message))
} catch (e: Exception) {
    AiResult.Failure(AiError.Unknown(e.message))
}

private fun HttpException.toAiError(json: Json): AiError {
    val body = runCatching { response()?.errorBody()?.string() }.getOrNull()
    val dto = body
        ?.takeIf { it.isNotBlank() }
        ?.let { runCatching { json.decodeFromString(AiErrorDto.serializer(), it) }.getOrNull() }
    val message = dto?.error
    val code = dto?.code
    // Present on the 429 rate-limit response; absent on the daily-quota one.
    val retryAfter = response()?.headers()?.get("Retry-After")?.trim()?.toIntOrNull()

    return when {
        code == AiErrorCode.UNAUTHORIZED -> AiError.NotAuthenticated(message)
        code == AiErrorCode.SUBSCRIPTION_REQUIRED -> AiError.NotSubscribed(message)
        code == AiErrorCode.NO_PROVIDER_CONFIGURED -> AiError.ProviderUnconfigured(message)
        code == AiErrorCode.QUOTA_EXCEEDED -> AiError.QuotaExceeded(message)
        code == AiErrorCode.RATE_LIMITED -> AiError.RateLimited(message, retryAfter)
        code == AiErrorCode.INVALID_INPUT -> AiError.InvalidInput(message)
        code == AiErrorCode.INVALID_AI_OUTPUT || code == AiErrorCode.REFUSED ->
            AiError.InvalidOutput(message)
        code == AiErrorCode.PROVIDER_ERROR -> AiError.ProviderFailure(message)
        // A restricted/suspended/probation account is forbidden for a reason a
        // subscription would not fix, so it must not become an upsell.
        code?.startsWith(AiErrorCode.ACCOUNT_PREFIX) == true -> AiError.Forbidden(message)
        else -> fromStatus(code(), message, retryAfter)
    }
}

/** Fallback for a response that carried no `code`. */
private fun fromStatus(status: Int, message: String?, retryAfter: Int?): AiError = when (status) {
    401 -> AiError.NotAuthenticated(message)
    // The only 403 the AI routes document is the subscriber gate.
    403 -> AiError.NotSubscribed(message)
    409 -> AiError.ProviderUnconfigured(message)
    422 -> AiError.InvalidInput(message)
    // Both 429s are code-tagged in practice; `Retry-After` is what separates the
    // short-window limiter from the daily allowance when they are not.
    429 -> if (retryAfter != null) AiError.RateLimited(message, retryAfter) else AiError.QuotaExceeded(message)
    in 500..599 -> AiError.ProviderFailure(message)
    else -> AiError.Unknown(message ?: "HTTP $status")
}
