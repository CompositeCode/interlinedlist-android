package com.interlinedlist.android.core.network.dto

import kotlinx.serialization.Serializable

/** `GET /api/user` wraps the user object: `{ "user": { ... } }`. */
@Serializable
data class CurrentUserResponse(
    val user: UserDto,
)
