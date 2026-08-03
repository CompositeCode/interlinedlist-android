package com.interlinedlist.android.feature.profile.domain

/**
 * A user entry in the current user's blocked or muted list (Milestone D). Carries just
 * enough to render a row and to un-block / un-mute by [username] (the mutation endpoints
 * key on the username, mirroring the block/mute POST/DELETE routes).
 */
data class ModeratedUser(
    val id: String,
    val username: String,
    val displayName: String?,
    val avatarUrl: String?,
) {
    /** The best label to show for the user: display name if set, else the @username. */
    val displayLabel: String
        get() = displayName?.takeIf { it.isNotBlank() } ?: "@$username"
}

/**
 * The current user's moderation relationship to another user, read alongside the
 * other-user profile so its overflow menu can reflect the blocked / muted state.
 */
data class ModerationStatus(
    val isBlocked: Boolean = false,
    val isMuted: Boolean = false,
)

/**
 * The canned reasons offered when reporting a user. The wire value ([apiValue]) is sent
 * as the report body's `reason`; the [label] is what the picker shows.
 */
enum class ReportReason(val apiValue: String, val label: String) {
    SPAM("spam", "Spam"),
    HARASSMENT("harassment", "Harassment or bullying"),
    IMPERSONATION("impersonation", "Impersonation"),
    INAPPROPRIATE("inappropriate", "Inappropriate content"),
    OTHER("other", "Something else"),
}
