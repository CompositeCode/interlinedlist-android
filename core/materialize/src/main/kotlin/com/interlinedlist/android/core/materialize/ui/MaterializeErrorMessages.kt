package com.interlinedlist.android.core.materialize.ui

import com.interlinedlist.android.core.common.result.AppError

/**
 * Maps a normalised [AppError] to what the window shows.
 *
 * `bad_request` / `validation_failed` arrive as [AppError.Unknown] carrying the
 * server's own words ("A list title is required", "Field 'year' has invalid
 * type"), which are far more useful than anything generic, so they are shown
 * verbatim.
 */
fun AppError.toUserMessage(): String = when (this) {
    is AppError.Network -> "No connection. Nothing was created."
    is AppError.Unauthorized -> message ?: "Please sign in again."
    is AppError.Forbidden -> message ?: "Your account cannot create this right now."
    is AppError.NotFound ->
        message ?: "Some of what you selected is no longer available."

    is AppError.RateLimited -> "Too many requests. Please wait a moment and try again."
    is AppError.SubscriptionRequired ->
        message ?: "Creating lists and documents requires an active subscription."

    is AppError.Server -> "InterlinedList is having trouble right now. Try again shortly."
    else -> message ?: "Something went wrong. Nothing was created."
}

/** True when the error is the subscriber-only gate, so the UI can show an upsell. */
val AppError.isSubscriptionGate: Boolean get() = this is AppError.SubscriptionRequired
