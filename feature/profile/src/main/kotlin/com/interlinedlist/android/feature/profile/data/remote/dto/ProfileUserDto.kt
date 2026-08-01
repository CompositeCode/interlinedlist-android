package com.interlinedlist.android.feature.profile.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Wire model for a user object returned by the profile endpoints
 * (`GET /api/user`, `GET /api/users/{username}`). The API is inconsistent about
 * the avatar field name — some responses use `avatarUrl`, others `avatar` — so
 * both are accepted and resolved by [avatar].
 */
@Serializable
data class ProfileUserDto(
    val id: String,
    val username: String = "",
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val avatar: String? = null,
    val bio: String? = null,
    val customerStatus: String? = null,
) {
    /** The avatar URL under whichever field the endpoint populated. */
    val avatarOrNull: String? get() = avatarUrl ?: avatar
}
