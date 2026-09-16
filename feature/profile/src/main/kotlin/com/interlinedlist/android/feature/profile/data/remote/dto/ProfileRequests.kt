package com.interlinedlist.android.feature.profile.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

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
 *
 * [latitude] and [longitude] are the exception: they are typed as [JsonElement] so
 * the profile location can be **cleared**. With `explicitNulls = false` a Kotlin
 * `null` is dropped from the body, which is how "leave this field alone" is said, so
 * there would otherwise be no way to say "unset it". A `JsonNull` is not a Kotlin
 * null, so it survives serialisation and goes out as a literal `null`; a
 * `JsonPrimitive(47.6062)` goes out as the number the live API returns. Nothing else
 * needs this, so nothing else pays for it.
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
    val latitude: JsonElement? = null,
    val longitude: JsonElement? = null,
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
