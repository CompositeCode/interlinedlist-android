package com.interlinedlist.android.feature.profile.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Body for `PATCH /api/user/update`. Only the profile fields this module edits are
 * modelled; null fields are omitted (see the module's `explicitNulls = false`
 * JSON config) so the server leaves the rest unchanged.
 */
@Serializable
data class UpdateProfileRequest(
    val displayName: String? = null,
    val bio: String? = null,
    val avatar: String? = null,
)

/** Body for `POST /api/user/avatar/from-url`. */
@Serializable
data class AvatarFromUrlRequest(
    val url: String,
)
