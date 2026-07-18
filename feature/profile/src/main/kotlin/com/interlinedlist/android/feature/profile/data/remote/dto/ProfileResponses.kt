package com.interlinedlist.android.feature.profile.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * `GET /api/user` and `PATCH /api/user/update` wrap the user in `{ "user": {...} }`.
 * [userOrSelf] tolerates a bare top-level user object as well, since some endpoints
 * inline it.
 */
@Serializable
data class ProfileResponse(
    val user: ProfileUserDto? = null,
    val id: String? = null,
    val username: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val avatar: String? = null,
    val bio: String? = null,
    val customerStatus: String? = null,
) {
    /** The user payload, whether wrapped under `user` or inlined at the top level. */
    val userOrSelf: ProfileUserDto?
        get() = user ?: id?.let {
            ProfileUserDto(
                id = it,
                username = username ?: "",
                displayName = displayName,
                avatarUrl = avatarUrl,
                avatar = avatar,
                bio = bio,
                customerStatus = customerStatus,
            )
        }
}

/**
 * `GET /api/users/search` and `GET /api/users/lookup`. Results may arrive under
 * `users` or the generic `data` envelope; both are accepted by [usersOrEmpty].
 */
@Serializable
data class UserSearchResponse(
    val users: List<ProfileUserDto>? = null,
    val data: List<ProfileUserDto>? = null,
) {
    val usersOrEmpty: List<ProfileUserDto> get() = users ?: data ?: emptyList()
}

/**
 * `POST /api/user/avatar/from-url` and `POST /api/user/avatar/upload` return the
 * new avatar location. The field name varies, so both are accepted; the updated
 * user object may also be echoed back under `user`.
 */
@Serializable
data class AvatarResponse(
    val avatarUrl: String? = null,
    val avatar: String? = null,
    val url: String? = null,
    val user: ProfileUserDto? = null,
) {
    /** The resolved avatar URL from whichever field the endpoint populated. */
    val avatarOrNull: String?
        get() = avatarUrl ?: avatar ?: url ?: user?.avatarOrNull
}
