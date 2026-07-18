package com.interlinedlist.android.feature.organizations.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Wire models for the organization members endpoints. A member row arrives either
 * flattened (`userId`/`username` on the row) or with a nested `user` object — the
 * mapper ([com.interlinedlist.android.feature.organizations.data.MemberMapper])
 * tolerates both so a member is never dropped for a missing field.
 */
@Serializable
data class MemberDto(
    val id: String? = null,
    val userId: String? = null,
    val role: String? = null,
    val username: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val avatar: String? = null,
    @Serializable(with = FlexibleBooleanSerializer::class)
    val active: Boolean? = null,
    val user: MemberUserDto? = null,
) {
    val resolvedAvatar: String? get() = avatarUrl ?: avatar ?: user?.resolvedAvatar
}

/** A user reference nested on a member row or returned by the org user search. */
@Serializable
data class MemberUserDto(
    val id: String,
    val username: String = "",
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val avatar: String? = null,
) {
    val resolvedAvatar: String? get() = avatarUrl ?: avatar
}

/** Envelope for `GET /api/organizations/{id}/members`; members may be wrapped or bare. */
@Serializable
data class MembersResponse(
    val data: List<MemberDto>? = null,
    val members: List<MemberDto>? = null,
    val pagination: PaginationDto? = null,
) {
    val items: List<MemberDto> get() = data ?: members ?: emptyList()
}

/** Envelope for `GET /api/organizations/{id}/users` (candidate users to add). */
@Serializable
data class OrgUsersResponse(
    val data: List<MemberUserDto>? = null,
    val users: List<MemberUserDto>? = null,
    val pagination: PaginationDto? = null,
) {
    val items: List<MemberUserDto> get() = data ?: users ?: emptyList()
}

/** Body for `POST /api/organizations/{id}/members` — add one user with a role. */
@Serializable
data class AddMemberRequest(
    val userId: String,
    val role: String? = null,
)

/** Body for `PUT /api/organizations/{id}/members/{userId}` — change role/active. */
@Serializable
data class UpdateMemberRequest(
    val role: String? = null,
    val active: String? = null,
)
