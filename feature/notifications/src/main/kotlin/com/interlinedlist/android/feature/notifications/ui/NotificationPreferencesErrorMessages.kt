package com.interlinedlist.android.feature.notifications.ui

import com.interlinedlist.android.core.common.result.AppError

/** Maps a normalised [AppError] to a concise, user-facing message for the preferences UI. */
fun AppError.toPreferencesMessage(): String = when (this) {
    is AppError.Network -> "No connection. Check your network and try again."
    is AppError.Unauthorized -> "Your session expired. Please sign in again."
    is AppError.SubscriptionRequired -> message ?: "This setting requires an active subscription."
    is AppError.NotFound -> "That preference is no longer available."
    is AppError.RateLimited -> "Slow down a moment and try again."
    is AppError.Server -> "InterlinedList is having trouble right now. Try again shortly."
    else -> message ?: "Couldn't save your change. Please try again."
}
