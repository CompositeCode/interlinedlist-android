package com.interlinedlist.android.feature.profile.domain

/**
 * A user entry in a followers / following list or a pending follow request. Tapping
 * one drills down into that user's full profile by [username], mirroring how search
 * results open a profile.
 */
data class FollowUser(
    val id: String,
    val username: String,
    val displayName: String?,
    val avatarUrl: String?,
) {
    /** The best label to show for the user: display name if set, else the @username. */
    val displayLabel: String
        get() = displayName?.takeIf { it.isNotBlank() } ?: "@$username"
}
