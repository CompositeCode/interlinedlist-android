package com.interlinedlist.android.feature.profile.data.remote.dto

import com.interlinedlist.android.feature.profile.domain.LinkedIdentity
import com.interlinedlist.android.feature.profile.domain.LoginSession
import kotlinx.serialization.Serializable

/**
 * `GET /api/user/sessions` → `{ "sessions": [ ... ] }` (shape verified live 2026-07-31).
 * The generic `data` envelope is tolerated too in case the server ever switches keys.
 */
@Serializable
data class SessionsResponse(
    val sessions: List<LoginSessionDto>? = null,
    val data: List<LoginSessionDto>? = null,
) {
    val sessionsOrEmpty: List<LoginSessionDto> get() = sessions ?: data ?: emptyList()
}

/** A single active login session (sync token). */
@Serializable
data class LoginSessionDto(
    val id: String,
    val deviceLabel: String? = null,
    val createdAt: String? = null,
    val lastUsedAt: String? = null,
    val isCurrent: Boolean = false,
) {
    fun toDomain(): LoginSession = LoginSession(
        id = id,
        deviceLabel = deviceLabel.orEmpty(),
        createdAt = createdAt,
        lastUsedAt = lastUsedAt,
        isCurrent = isCurrent,
    )
}

/**
 * `GET /api/user/identities` → `{ "identities": [ ... ] }` (shape verified live 2026-07-31).
 * The generic `data` envelope is tolerated too.
 */
@Serializable
data class IdentitiesResponse(
    val identities: List<LinkedIdentityDto>? = null,
    val data: List<LinkedIdentityDto>? = null,
) {
    val identitiesOrEmpty: List<LinkedIdentityDto> get() = identities ?: data ?: emptyList()
}

/** A single linked social identity. */
@Serializable
data class LinkedIdentityDto(
    val id: String,
    val provider: String = "",
    val providerUsername: String? = null,
    val profileUrl: String? = null,
    val avatarUrl: String? = null,
    val connectedAt: String? = null,
    val lastVerifiedAt: String? = null,
) {
    fun toDomain(): LinkedIdentity = LinkedIdentity(
        id = id,
        provider = provider,
        providerUsername = providerUsername,
        profileUrl = profileUrl,
        avatarUrl = avatarUrl,
        connectedAt = connectedAt,
    )
}
