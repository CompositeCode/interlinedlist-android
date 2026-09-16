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
 * The server's guard against orphaning an organization. Both halves are plain 400s
 * with `code: "bad_request"`, so they are detected on the message (verified live):
 *
 * - `{"error":"Cannot remove the last owner"}` — removing, or leaving as, the sole owner
 * - `{"error":"Cannot demote the last owner"}` — changing the sole owner's role
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

/**
 * Explains why a role change was refused. The server refuses to demote the only
 * owner, which would leave the organization without one.
 */
fun AppError.toRoleChangeMessage(): String = when {
    isLastOwnerRejection -> LAST_OWNER_DEMOTE_EXPLANATION
    else -> toUserMessage()
}

/** Explains why removing a member was refused — most often the last-owner guard. */
fun AppError.toRemoveMemberMessage(): String = when {
    isLastOwnerRejection -> LAST_OWNER_REMOVE_EXPLANATION
    else -> toUserMessage()
}

/**
 * Shown both before the attempt (the UI can see there is only one owner) and after
 * the server refuses with "Cannot demote the last owner", so the paths read alike.
 */
const val LAST_OWNER_DEMOTE_EXPLANATION: String =
    "This is the organization's only owner. Make someone else an owner first, " +
        "then you can change this role."

/** The removal counterpart of [LAST_OWNER_DEMOTE_EXPLANATION]. */
const val LAST_OWNER_REMOVE_EXPLANATION: String =
    "This is the organization's only owner. Make someone else an owner first, " +
        "then you can remove them."
