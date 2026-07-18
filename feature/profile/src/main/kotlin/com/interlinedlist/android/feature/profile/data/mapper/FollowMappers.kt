package com.interlinedlist.android.feature.profile.data.mapper

import com.interlinedlist.android.feature.profile.data.remote.dto.FollowCountsResponse
import com.interlinedlist.android.feature.profile.data.remote.dto.FollowRequestDto
import com.interlinedlist.android.feature.profile.data.remote.dto.FollowStatusResponse
import com.interlinedlist.android.feature.profile.data.remote.dto.ProfileUserDto
import com.interlinedlist.android.feature.profile.domain.FollowCounts
import com.interlinedlist.android.feature.profile.domain.FollowStatus
import com.interlinedlist.android.feature.profile.domain.FollowUser

/** Maps a wire user into a lightweight [FollowUser] for list/request rows. */
fun ProfileUserDto.toFollowUser(): FollowUser = FollowUser(
    id = id,
    username = username,
    displayName = displayName,
    avatarUrl = avatarOrNull,
)

/** Maps the status envelope to the domain [FollowStatus] (SELF is decided elsewhere). */
fun FollowStatusResponse.toFollowStatus(): FollowStatus = when (resolvedStatus) {
    "following" -> FollowStatus.FOLLOWING
    "requested" -> FollowStatus.REQUESTED
    else -> FollowStatus.NOT_FOLLOWING
}

/** Maps the counts envelope to the domain [FollowCounts]. */
fun FollowCountsResponse.toFollowCounts(): FollowCounts = FollowCounts(
    followers = followersOrZero,
    following = followingOrZero,
)

/** Maps a pending-request entry to a [FollowUser], dropping entries with no user. */
fun FollowRequestDto.toFollowUserOrNull(): FollowUser? =
    requesterOrSelf?.takeIf { it.id.isNotBlank() }?.toFollowUser()
