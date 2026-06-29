package com.interlinedlist.android.feature.auth.ui

import com.interlinedlist.android.core.common.result.AppError

/** Maps a normalised [AppError] to a concise, user-facing message for the auth UI. */
fun AppError.toUserMessage(): String = when (this) {
    is AppError.Unauthorized -> message ?: "Incorrect email or password."
    is AppError.Network -> "No connection. Check your network and try again."
    is AppError.RateLimited -> "Too many attempts. Please wait a moment and try again."
    is AppError.SubscriptionRequired -> message ?: "This feature requires an active subscription."
    is AppError.Server -> "InterlinedList is having trouble right now. Try again shortly."
    else -> message ?: "Something went wrong. Please try again."
}
