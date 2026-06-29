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
)
