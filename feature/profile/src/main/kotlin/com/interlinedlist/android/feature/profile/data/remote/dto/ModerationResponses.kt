package com.interlinedlist.android.feature.profile.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * `GET /api/user/blocks` → `{ "blockedUsers": [ ... ], "pagination": { ... } }`
 * (list shape verified live 2026-07-31). The generic `data`/`users` envelopes are
 * tolerated too in case the server ever switches keys.
 */
@Serializable
data class BlocksResponse(
    val blockedUsers: List<ModeratedUserDto>? = null,
    val users: List<ModeratedUserDto>? = null,
    val data: List<ModeratedUserDto>? = null,
) {
    val usersOrEmpty: List<ModeratedUserDto> get() = blockedUsers ?: users ?: data ?: emptyList()
}

/**
 * `GET /api/user/mutes` → `{ "mutedUsers": [ ... ], "pagination": { ... } }`
 * (list shape verified live 2026-07-31).
 */
@Serializable
data class MutesResponse(
    val mutedUsers: List<ModeratedUserDto>? = null,
    val users: List<ModeratedUserDto>? = null,
    val data: List<ModeratedUserDto>? = null,
) {
    val usersOrEmpty: List<ModeratedUserDto> get() = mutedUsers ?: users ?: data ?: emptyList()
}

/**
 * A user entry inside a blocks / mutes list. The inner user object shape is not modelled
 * in the OpenAPI spec (and both lists were empty for the probe account), so this tolerates
 * either a nested `user` object or fields inlined at the top level, mirroring how
 * [FollowRequestDto] handles the follow-requests list.
 */
@Serializable
data class ModeratedUserDto(
    val user: ProfileUserDto? = null,
    val id: String? = null,
    val userId: String? = null,
    val username: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val avatar: String? = null,
) {
    /** The moderated user, whether nested under `user` or inlined at the top level. */
    val userOrSelf: ProfileUserDto?
        get() = user ?: (id ?: userId)?.let { resolvedId ->
            ProfileUserDto(
                id = resolvedId,
                username = username ?: "",
                displayName = displayName,
                avatarUrl = avatarUrl,
                avatar = avatar,
            )
        }
}

/**
 * `GET /api/users/{username}/block` → `{ "blocked": true }` (shape verified live 2026-07-31).
 */
@Serializable
data class BlockStatusResponse(
    val blocked: Boolean = false,
)

/**
 * `GET /api/users/{username}/mute` → `{ "muted": true }` (shape verified live 2026-07-31).
 */
@Serializable
data class MuteStatusResponse(
    val muted: Boolean = false,
)

/**
 * Body for `POST /api/users/{username}/report`. The OpenAPI spec models `reason` and an
 * optional free-text `detail` (singular — confirmed from the spec, not `details`).
 */
@Serializable
data class ReportUserRequest(
    val reason: String,
    val detail: String? = null,
)
