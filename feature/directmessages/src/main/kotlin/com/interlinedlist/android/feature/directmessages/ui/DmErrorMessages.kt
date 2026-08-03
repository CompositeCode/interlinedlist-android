package com.interlinedlist.android.feature.directmessages.ui

import com.interlinedlist.android.core.common.result.AppError

/** Maps a normalised [AppError] to a concise, user-facing message for the DM UI. */
fun AppError.toUserMessage(): String = when (this) {
    is AppError.Network -> "No connection. Check your network and try again."
    is AppError.Unauthorized -> "Your session expired. Please sign in again."
    is AppError.Forbidden -> message ?: "You can't message this person."
    is AppError.NotFound -> message ?: "That conversation is no longer available."
    is AppError.RateLimited -> "You're sending messages too quickly. Try again shortly."
    is AppError.SubscriptionRequired -> message ?: "This feature requires an active subscription."
    is AppError.Server -> "InterlinedList is having trouble right now. Try again shortly."
    else -> message ?: "Something went wrong. Please try again."
}
