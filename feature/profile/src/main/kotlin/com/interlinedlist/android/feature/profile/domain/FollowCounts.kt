package com.interlinedlist.android.feature.profile.domain

/**
 * Follower / following tallies for a user, shown as tappable counts on the profile
 * header. Defaults to zero so the UI always has something to render before the
 * counts load.
 */
data class FollowCounts(
    val followers: Int = 0,
    val following: Int = 0,
)
