package com.interlinedlist.android.core.model

/**
 * The authenticated (or publicly viewed) InterlinedList user.
 *
 * Mirrors the fields returned by `GET /api/users/me` and `GET /api/users/{username}`,
 * normalised into a platform-independent domain type.
 */
data class User(
    val id: String,
    val username: String,
    val displayName: String?,
    val email: String?,
    val avatarUrl: String?,
    val bio: String?,
    val emailVerified: Boolean,
    val customerStatus: CustomerStatus,
    /**
     * The account's default post visibility (Settings -> Message Settings on the
     * web). Seeds the composer's Public/Private toggle; a per-message choice
     * overrides it. Defaults to public, matching the server default.
     */
    val defaultPubliclyVisible: Boolean = true,
)
