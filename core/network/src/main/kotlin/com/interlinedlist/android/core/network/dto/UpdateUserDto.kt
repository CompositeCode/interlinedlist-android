package com.interlinedlist.android.core.network.dto

import kotlinx.serialization.Serializable

/**
 * A **partial** body for `PATCH /api/user/update`. Every field is optional and the
 * shared Json uses `explicitNulls = false`, so an untouched field is omitted from
 * the request entirely and can never clobber another preference.
 *
 * Only the fields a `:core:network` consumer actually needs are modelled — today
 * that is `viewingPreference`, which the messages feed writes when the user picks a
 * view. `:feature:profile` owns the full fifteen-field settings surface; see
 * [com.interlinedlist.android.core.network.preferences.ViewingPreferenceStore] for
 * why the two coexist.
 */
@Serializable
data class UpdateUserRequest(
    val viewingPreference: String? = null,
)

/**
 * Response to `PATCH /api/user/update`: the updated user, "same shape as
 * `GET /api/user`" per `/help/api/users-and-profile`.
 *
 * Live, `GET /api/user` wraps the object as `{ "user": … }` while the help centre's
 * example shows it inlined at the top level, so both are tolerated. Every field is
 * optional: a thin acknowledgement body must not fail the call.
 */
@Serializable
data class UpdateUserResponse(
    val user: UserDto? = null,
    val viewingPreference: String? = null,
) {
    /** The saved `viewingPreference`, wrapped or inlined, or null if not echoed. */
    val savedViewingPreference: String? get() = user?.viewingPreference ?: viewingPreference
}
