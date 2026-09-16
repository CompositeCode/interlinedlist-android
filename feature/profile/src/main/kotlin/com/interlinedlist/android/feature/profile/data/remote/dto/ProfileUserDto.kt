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
    /**
     * The address a requested email change is waiting on, or null when no change is
     * in flight. Present on `GET /api/user` for the signed-in user only (confirmed
     * live); other users' profiles omit it.
     */
    val pendingEmail: String? = null,
    // --- Preference fields (present on `GET /api/user` for the signed-in user
    // only; other users' public profiles omit them, hence all-nullable). ---
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
) {
    /** The avatar URL under whichever field the endpoint populated. */
    val avatarOrNull: String? get() = avatarUrl ?: avatar
}
