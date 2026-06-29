package com.interlinedlist.android.core.common.result

/**
 * Outcome of a repository operation that can fail. Lets the UI layer render
 * success/error states without try/catch plumbing.
 */
sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Failure(val error: AppError) : ApiResult<Nothing>
}

/** Transforms the success value, propagating failures unchanged. */
inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(data))
    is ApiResult.Failure -> this
}
