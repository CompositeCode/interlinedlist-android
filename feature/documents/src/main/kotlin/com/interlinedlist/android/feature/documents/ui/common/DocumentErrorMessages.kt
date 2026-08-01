package com.interlinedlist.android.feature.documents.ui.common

import com.interlinedlist.android.core.common.result.AppError

/** Maps a normalised [AppError] to a concise, user-facing message for the docs UI. */
fun AppError.toUserMessage(): String = when (this) {
    is AppError.Network -> "No connection. Check your network and try again."
    is AppError.Unauthorized -> message ?: "Please sign in again."
    is AppError.SubscriptionRequired -> message ?: "Documents require an active subscription."
    is AppError.NotFound -> message ?: "That document could not be found."
    is AppError.RateLimited -> "Too many requests. Please wait a moment and try again."
    is AppError.Server -> "InterlinedList is having trouble right now. Try again shortly."
    else -> message ?: "Something went wrong. Please try again."
}

/** Whether the error is the subscriber-only gate, so the UI can show an upsell. */
val AppError.isSubscriptionGate: Boolean get() = this is AppError.SubscriptionRequired
