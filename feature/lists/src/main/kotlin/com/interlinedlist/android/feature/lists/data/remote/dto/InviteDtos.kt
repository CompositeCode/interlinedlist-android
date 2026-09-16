package com.interlinedlist.android.feature.lists.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Wire models for the list email-invite endpoints.
 *
 * The help centre (`/help/api/sharing` → *Email invites*) documents the list row as
 * `{ email, role, expiresAt, accepted, createdAt, token }` and the 201 create body as
 * `{ email, role, expiresAt, url }` — note the create response carries **no token**;
 * it is embedded in `url`. The share-invite schema additionally declares `id`,
 * `listId`, `invitedByUserId`, `revokedAt`, `acceptedAt`, `acceptedByUserId`, so all
 * of those are modelled optionally: whichever projection the server returns, the DTO
 * parses. Everything defaults, and the shared Json ignores unknown keys.
 */
@Serializable
data class ListInviteDto(
    val id: String = "",
    val listId: String? = null,
    val email: String = "",
    val token: String = "",
    val role: String? = null,
    val invitedByUserId: String? = null,
    val expiresAt: String? = null,
    val revokedAt: String? = null,
    /** Reported on the list projection; the full entity reports `acceptedAt` instead. */
    val accepted: Boolean = false,
    val acceptedAt: String? = null,
    val acceptedByUserId: String? = null,
    val createdAt: String? = null,
    /** The invite landing address; only the create response is documented to return it. */
    val url: String? = null,
)

/** `GET /api/lists/{id}/invites` — documented as `{ "invites": [...] }`. */
@Serializable
data class ListInvitesResponse(
    val invites: List<ListInviteDto>? = null,
    val data: List<ListInviteDto>? = null,
) {
    val items: List<ListInviteDto> get() = invites ?: data ?: emptyList()
}

/**
 * `POST /api/lists/{id}/invites` — the documented 201 returns the invite fields
 * inline; a wrapped `{ "invite": ... }` / `{ "data": ... }` envelope (the convention
 * other create endpoints use) is accepted too.
 */
@Serializable
data class ListInviteEnvelope(
    val invite: ListInviteDto? = null,
    val data: ListInviteDto? = null,
    val id: String? = null,
    val email: String? = null,
    val token: String? = null,
    val role: String? = null,
    val expiresAt: String? = null,
    val revokedAt: String? = null,
    val accepted: Boolean = false,
    val acceptedAt: String? = null,
    val createdAt: String? = null,
    val url: String? = null,
) {
    /** The created invite, whether wrapped or inlined on the response root. */
    val inviteOrSelf: ListInviteDto?
        get() = invite ?: data ?: (email ?: url)?.let {
            ListInviteDto(
                id = id.orEmpty(),
                email = email.orEmpty(),
                token = token.orEmpty(),
                role = role,
                expiresAt = expiresAt,
                revokedAt = revokedAt,
                accepted = accepted,
                acceptedAt = acceptedAt,
                createdAt = createdAt,
                url = url,
            )
        }
}

/**
 * Body for `POST /api/lists/{id}/invites`. `role` defaults server-side to `watcher`;
 * `expiresAt` is optional (null == never expires) and is dropped by the shared Json
 * when null.
 */
@Serializable
data class CreateInviteRequest(
    val email: String,
    val role: String,
    val expiresAt: String? = null,
)
