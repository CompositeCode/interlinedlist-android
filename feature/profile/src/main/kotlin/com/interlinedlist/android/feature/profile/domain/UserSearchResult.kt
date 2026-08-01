package com.interlinedlist.android.feature.profile.domain

/**
 * A lightweight user entry returned by `GET /api/users/search`. Search results are
 * one-shot (not cached); tapping one drills down into that user's full profile by
 * [username].
 */
data class UserSearchResult(
    val id: String,
    val username: String,
    val displayName: String?,
    val avatarUrl: String?,
) {
    /** The best label to show for the user: display name if set, else the @username. */
    val displayLabel: String
        get() = displayName?.takeIf { it.isNotBlank() } ?: "@$username"
}
