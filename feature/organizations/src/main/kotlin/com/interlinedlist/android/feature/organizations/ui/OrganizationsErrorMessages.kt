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

/**
 * The server's guard against orphaning an organization: a sole owner may not leave
 * (400 `{"error":"Cannot remove the last owner"}`). Detected on the message because
 * the status code is a plain bad request.
 */
val AppError.isLastOwnerRejection: Boolean
    get() = message?.contains("last owner", ignoreCase = true) == true

/** Explains why a join was refused instead of showing a bare API string. */
fun AppError.toJoinMessage(): String = when (this) {
    is AppError.Conflict -> "You're already a member of this organization."
    is AppError.Forbidden -> "This organization is private. Ask an owner or admin to add you."
    is AppError.NotFound -> "That organization could not be found."
    else -> toUserMessage()
}

/**
 * Explains why a leave was refused. The last-owner rejection is spelled out — the
 * organization would be left without an owner — so the user knows what to do
 * instead of seeing a generic failure.
 */
fun AppError.toLeaveMessage(): String = when {
    isLastOwnerRejection -> LAST_OWNER_EXPLANATION
    else -> toUserMessage()
}

/**
 * Shown both before the attempt (when the UI can see you are the only owner) and
 * after the server refuses, so the two paths read the same.
 */
const val LAST_OWNER_EXPLANATION: String =
    "You're the only owner of this organization. Make another member an owner, " +
        "or delete the organization, before you leave."
