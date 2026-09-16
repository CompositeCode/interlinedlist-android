package com.interlinedlist.android.feature.profile.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Body for `PATCH /api/user/update`. Every field the endpoint accepts is modelled
 * (verified against the OpenAPI spec) and every one is optional: null fields are
 * omitted by the module's `explicitNulls = false` JSON config, so a partial PATCH
 * only ever touches what the caller set.
 *
 * The values are sent with their natural JSON types (`Int`, `Boolean`, `Double`),
 * matching the types the same fields come back with on `UserWire`. The generated
 * spec types several of them as `string` because the route handler coerces its
 * input, which native types satisfy too.
 */
@Serializable
data class UpdateProfileRequest(
    val displayName: String? = null,
    val bio: String? = null,
    val avatar: String? = null,
    val theme: String? = null,
    val maxMessageLength: Int? = null,
    val defaultPubliclyVisible: Boolean? = null,
    val messagesPerPage: Int? = null,
    val viewingPreference: String? = null,
    val showPreviews: Boolean? = null,
    val showAdvancedPostSettings: Boolean? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isPrivateAccount: Boolean? = null,
    val githubDefaultRepo: String? = null,
    val notificationTrayLimit: Int? = null,
)

/** Body for `POST /api/user/avatar/from-url`. */
@Serializable
data class AvatarFromUrlRequest(
    val url: String,
)

/**
 * Body for `POST /api/user/change-email/request` (schema verified against the
 * OpenAPI spec). The server sends a verification email to [newEmail].
 */
@Serializable
data class ChangeEmailRequest(
    val newEmail: String,
)

/**
 * Body for `POST /api/user/delete` (schema verified against the OpenAPI spec). The
 * account is deleted only when both [username] and [email] match the current user —
 * the type-to-confirm guard on the UI collects them.
 */
@Serializable
data class DeleteAccountRequest(
    val username: String,
    val email: String,
)
