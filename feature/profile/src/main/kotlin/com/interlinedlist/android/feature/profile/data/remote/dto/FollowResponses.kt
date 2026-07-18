package com.interlinedlist.android.feature.profile.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * `GET /api/follow/{userId}/status`. The API is undocumented about the exact shape,
 * so this tolerates the common conventions: an explicit `status` string
 * (`following` / `requested` / `pending` / `none`), or boolean flags
 * (`isFollowing` / `requested`). [resolvedStatus] normalises them to one of
 * `following` / `requested` / `none`.
 */
@Serializable
data class FollowStatusResponse(
    val status: String? = null,
    val isFollowing: Boolean? = null,
    val following: Boolean? = null,
    val requested: Boolean? = null,
    val isRequested: Boolean? = null,
    val pending: Boolean? = null,
) {
    /** Normalised status token: `following`, `requested`, or `none`. */
    val resolvedStatus: String
        get() {
            status?.lowercase()?.let { raw ->
                return when {
                    raw.contains("follow") && !raw.contains("not") -> "following"
                    raw.contains("request") || raw.contains("pending") -> "requested"
                    else -> "none"
                }
            }
            return when {
                isFollowing == true || following == true -> "following"
                requested == true || isRequested == true || pending == true -> "requested"
                else -> "none"
            }
        }
}

/**
 * `GET /api/follow/{userId}/counts`. Follower / following tallies. Field names vary
 * across similar APIs, so a couple of common aliases are accepted.
 */
@Serializable
data class FollowCountsResponse(
    val followers: Int? = null,
    val following: Int? = null,
    val followersCount: Int? = null,
    val followingCount: Int? = null,
) {
    val followersOrZero: Int get() = followers ?: followersCount ?: 0
    val followingOrZero: Int get() = following ?: followingCount ?: 0
}

/**
 * `GET /api/follow/{userId}/followers` and `/following`. A list of users under one
 * of the common envelope keys (`users`, `followers`, `following`, or the generic
 * `data`). [usersOrEmpty] reads whichever the server populated.
 */
@Serializable
data class FollowListResponse(
    val users: List<ProfileUserDto>? = null,
    val followers: List<ProfileUserDto>? = null,
    val following: List<ProfileUserDto>? = null,
    val data: List<ProfileUserDto>? = null,
) {
    val usersOrEmpty: List<ProfileUserDto>
        get() = users ?: followers ?: following ?: data ?: emptyList()
}

/**
 * A single pending follow request. The requester may be nested under `user` /
 * `follower` / `requester`, or inlined at the top level; [requesterOrSelf] resolves
 * whichever shape the server used.
 */
@Serializable
data class FollowRequestDto(
    val user: ProfileUserDto? = null,
    val follower: ProfileUserDto? = null,
    val requester: ProfileUserDto? = null,
    val id: String? = null,
    val username: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val avatar: String? = null,
) {
    /** The requesting user, whether nested or inlined. */
    val requesterOrSelf: ProfileUserDto?
        get() = user ?: follower ?: requester ?: id?.let {
            ProfileUserDto(
                id = it,
                username = username ?: "",
                displayName = displayName,
                avatarUrl = avatarUrl,
                avatar = avatar,
            )
        }
}

/**
 * `GET /api/follow/requests`. Pending requests under one of the common envelope keys
 * (`requests`, `users`, or the generic `data`).
 */
@Serializable
data class FollowRequestsResponse(
    val requests: List<FollowRequestDto>? = null,
    val users: List<FollowRequestDto>? = null,
    val data: List<FollowRequestDto>? = null,
) {
    val requestsOrEmpty: List<FollowRequestDto>
        get() = requests ?: users ?: data ?: emptyList()
}
