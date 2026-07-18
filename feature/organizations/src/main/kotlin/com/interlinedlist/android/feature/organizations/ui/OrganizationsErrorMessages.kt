package com.interlinedlist.android.feature.organizations.ui

import com.interlinedlist.android.core.common.result.AppError

/** Maps a normalised [AppError] to a concise, user-facing message for the Orgs UI. */
fun AppError.toUserMessage(): String = when (this) {
    is AppError.Network -> "No connection. Showing what's saved on this device."
    is AppError.Unauthorized -> message ?: "Please sign in again."
    is AppError.NotFound -> message ?: "That organization could not be found."
    is AppError.RateLimited -> "Too many requests. Please wait a moment and try again."
    is AppError.SubscriptionRequired -> message ?: "Organizations require an active subscription."
    is AppError.Server -> "InterlinedList is having trouble right now. Try again shortly."
    else -> message ?: "Something went wrong. Please try again."
}

/** True when the error is the subscriber-only gate, so the UI can show an upsell. */
val AppError.isSubscriptionGate: Boolean get() = this is AppError.SubscriptionRequired
