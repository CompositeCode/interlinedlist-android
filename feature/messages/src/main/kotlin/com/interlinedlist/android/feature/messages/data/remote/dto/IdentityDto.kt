package com.interlinedlist.android.feature.messages.data.remote.dto

import com.interlinedlist.android.feature.messages.domain.LinkedNetwork
import kotlinx.serialization.Serializable

/**
 * Response from `GET /api/user/identities`:
 * `{ "identities": [ { id, provider, providerUsername, profileUrl, avatarUrl,
 * connectedAt, lastVerifiedAt } ] }`.
 *
 * The endpoint isn't schema-modelled in the OpenAPI spec, so this mirrors the
 * verified live shape; the shared Json `ignoreUnknownKeys`, so extra fields (e.g.
 * `lastVerifiedAt`) are tolerated.
 */
@Serializable
data class IdentitiesResponse(
    val identities: List<IdentityDto> = emptyList(),
)

/** A single linked social identity. */
@Serializable
data class IdentityDto(
    val id: String,
    val provider: String,
    val providerUsername: String = "",
    val profileUrl: String? = null,
    val avatarUrl: String? = null,
    val connectedAt: String? = null,
)

/** Maps a wire identity into the domain [LinkedNetwork]. */
fun IdentityDto.toDomain(): LinkedNetwork = LinkedNetwork(
    id = id,
    provider = provider,
    providerUsername = providerUsername,
    profileUrl = profileUrl,
    avatarUrl = avatarUrl,
    connectedAt = connectedAt,
)
