package com.interlinedlist.android.feature.integrations.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * `GET /api/user/identities` → `{ "identities": [ ... ] }`. The OpenAPI spec types
 * the body as a bare `object`, so this mirrors the live shape already verified by
 * `:feature:profile` and `:feature:messages`; the generic `data` envelope is
 * tolerated as well in case the server ever switches keys.
 */
@Serializable
data class IdentitiesResponse(
    val identities: List<LinkedIdentityDto>? = null,
    val data: List<LinkedIdentityDto>? = null,
) {
    val identitiesOrEmpty: List<LinkedIdentityDto> get() = identities ?: data ?: emptyList()
}

/**
 * A single linked social identity. [provider] is the key both
 * `DELETE /api/user/identities?provider=` and `POST /api/user/identities/verify`
 * expect, and for Mastodon it carries the instance (`mastodon:techhub.social`).
 *
 * [lastVerifiedAt] is what makes a stale connection visible before a cross-post
 * silently fails; the API omits it until the connection has been checked once.
 */
@Serializable
data class LinkedIdentityDto(
    val id: String = "",
    val provider: String = "",
    val providerUsername: String? = null,
    val profileUrl: String? = null,
    val avatarUrl: String? = null,
    val connectedAt: String? = null,
    val lastVerifiedAt: String? = null,
)

/** Body for `POST /api/user/identities/verify`; the spec models exactly one field. */
@Serializable
data class VerifyIdentityRequest(val provider: String)
