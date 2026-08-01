package com.interlinedlist.android.feature.billing.ui

import com.interlinedlist.android.core.common.result.AppError

/** Maps a normalised [AppError] to a concise, user-facing message for the billing UI. */
fun AppError.toUserMessage(): String = when (this) {
    is AppError.Network -> "No connection. Check your network and try again."
    is AppError.Unauthorized -> message ?: "Please sign in again."
    is AppError.RateLimited -> "Too many requests. Please wait a moment and try again."
    is AppError.Server -> "We couldn't start your billing session. Please try again shortly."
    else -> message ?: "Something went wrong. Please try again."
}
