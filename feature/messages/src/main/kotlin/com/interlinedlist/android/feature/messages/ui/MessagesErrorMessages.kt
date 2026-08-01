package com.interlinedlist.android.feature.messages.ui

import com.interlinedlist.android.core.common.result.AppError

/** Maps a normalised [AppError] to a concise, user-facing message for the feed UI. */
fun AppError.toUserMessage(): String = when (this) {
    is AppError.Network -> "No connection. Check your network and try again."
    is AppError.Unauthorized -> "Your session expired. Please sign in again."
    is AppError.SubscriptionRequired -> message ?: "The feed requires an active subscription."
    is AppError.NotFound -> "This message is no longer available."
    is AppError.RateLimited -> "Slow down a moment and try again."
    is AppError.Server -> "InterlinedList is having trouble right now. Try again shortly."
    else -> message ?: "Something went wrong. Please try again."
}

/** True when the error should render the subscription upsell/locked state. */
val AppError.isSubscriptionGate: Boolean
    get() = this is AppError.SubscriptionRequired
