package com.interlinedlist.android.feature.organizations.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Wire models for the Organizations API. Field names follow the InterlinedList
 * REST contract; the shared [kotlinx.serialization.json.Json] is configured with
 * `ignoreUnknownKeys`, so extra server fields are tolerated and only the fields
 * the UI renders need declaring.
 *
 * The OpenAPI extract does not pin the response bodies, so these are modelled
 * defensively: booleans arrive as either JSON booleans or strings (see
 * [FlexibleBoolean]), counts arrive under several names, and member rows arrive
 * flattened or nested — the mappers reconcile the variants.
 */

/** An organization envelope as returned by index and detail endpoints. */
@Serializable
data class OrganizationDto(
    val id: String,
    val name: String = "",
    val description: String? = null,
    val avatar: String? = null,
    val avatarUrl: String? = null,
    @Serializable(with = FlexibleBooleanSerializer::class)
    val isPublic: Boolean? = null,
    @Serializable(with = FlexibleBooleanSerializer::class)
    val public: Boolean? = null,
    // The API reports the member count under a few different names.
    val memberCount: Int? = null,
    val membersCount: Int? = null,
    val members: Int? = null,
    // The current user's role in this org, when the endpoint includes it.
    val role: String? = null,
    val updatedAt: String? = null,
) {
    /** The avatar URL under whichever field name the API used. */
    val resolvedAvatar: String? get() = avatarUrl ?: avatar
    /** Public flag under either field name; unknown means private. */
    val resolvedPublic: Boolean get() = isPublic ?: public ?: false
    /** Member count under whichever name the API used, defaulting to zero. */
    val resolvedMemberCount: Int get() = memberCount ?: membersCount ?: members ?: 0
}

/** Pagination block shared by list endpoints. */
@Serializable
data class PaginationDto(
    val total: Int = 0,
    val limit: Int = 0,
    val offset: Int = 0,
    @Serializable(with = FlexibleBooleanSerializer::class)
    val hasMore: Boolean? = null,
)

/**
 * Envelope for `GET /api/organizations` and `GET /api/user/organizations`.
 * The payload may be `{ data: [...], pagination: {...} }` or bare under
 * `organizations`; both list keys are accepted.
 */
@Serializable
data class OrganizationsResponse(
    val data: List<OrganizationDto>? = null,
    val organizations: List<OrganizationDto>? = null,
    val pagination: PaginationDto? = null,
) {
    val items: List<OrganizationDto> get() = data ?: organizations ?: emptyList()
}

/** Envelope for `GET /api/organizations/{id}` and mutations; org may be wrapped or bare. */
@Serializable
data class OrganizationEnvelope(
    val organization: OrganizationDto? = null,
    val data: OrganizationDto? = null,
) {
    val org: OrganizationDto? get() = organization ?: data
}

/** Body for `POST /api/organizations`. `isPublic` is serialised as a string per the API. */
@Serializable
data class CreateOrganizationRequest(
    val name: String,
    val description: String? = null,
    val avatar: String? = null,
    val isPublic: String? = null,
)

/** Body for `PUT /api/organizations/{id}` — partial metadata updates. */
@Serializable
data class UpdateOrganizationRequest(
    val name: String? = null,
    val description: String? = null,
    val avatar: String? = null,
    val isPublic: String? = null,
)
