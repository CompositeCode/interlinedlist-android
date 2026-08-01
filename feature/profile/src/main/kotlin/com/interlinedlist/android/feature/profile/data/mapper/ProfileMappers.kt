package com.interlinedlist.android.feature.profile.data.mapper

import com.interlinedlist.android.core.model.CustomerStatus
import com.interlinedlist.android.feature.profile.data.remote.dto.ProfileUserDto
import com.interlinedlist.android.feature.profile.domain.ProfileUser
import com.interlinedlist.android.feature.profile.domain.UserSearchResult

/**
 * Maps a wire user into the domain [ProfileUser]. [isCurrentUser] is decided by the
 * repository (the current user knows their own id) rather than the wire, since the
 * same DTO shape is used for both `GET /api/user` and `GET /api/users/{username}`.
 */
fun ProfileUserDto.toProfileUser(isCurrentUser: Boolean): ProfileUser = ProfileUser(
    id = id,
    username = username,
    displayName = displayName,
    avatarUrl = avatarOrNull,
    bio = bio,
    customerStatus = CustomerStatus.fromApiValue(customerStatus),
    isCurrentUser = isCurrentUser,
)

/** Maps a wire user into a lightweight [UserSearchResult]. */
fun ProfileUserDto.toSearchResult(): UserSearchResult = UserSearchResult(
    id = id,
    username = username,
    displayName = displayName,
    avatarUrl = avatarOrNull,
)
