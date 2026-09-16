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

/**
 * Invite-specific wording. The invite endpoints reject with a useful `error`
 * string of their own (invalid address, invalid role, unparseable expiry, the
 * subscriber gate), so the server's message is preferred wherever it exists and
 * only the fallbacks are re-worded for the invite context — a 404 here means the
 * invite (or the caller's ownership of the document) is gone, not the document.
 */
fun AppError.toInviteMessage(): String = when (this) {
    is AppError.Network -> "No connection. Check your network and try again."
    is AppError.SubscriptionRequired -> message ?: "Subscribe to invite people to documents."
    is AppError.Forbidden -> message ?: "Only the document owner can manage invites."
    is AppError.NotFound -> message ?: "That invite is no longer available."
    is AppError.Conflict -> message ?: "That person already has access to this document."
    is AppError.RateLimited -> "Too many invites just now. Please wait a moment and try again."
    is AppError.Server -> "InterlinedList is having trouble right now. Try again shortly."
    else -> message ?: "That invite could not be sent. Please try again."
}
