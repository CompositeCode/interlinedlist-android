package com.interlinedlist.android.feature.documents.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Wire models for the document email-invite endpoints.
 *
 * The help centre (`/help/api/sharing` → *Email invites*) documents the list row as
 * `{ email, role, expiresAt, accepted, createdAt, token }` and the 201 create body as
 * `{ email, role, expiresAt, url }` — note the create response carries **no token**;
 * it is embedded in `url`. The `DocumentShareInvite` schema additionally declares
 * `id`, `documentId`, `invitedByUserId`, `revokedAt`, `acceptedAt`, `acceptedByUserId`,
 * so all of those are modelled optionally: whichever projection the server returns,
 * the DTO parses. Everything defaults, and the shared Json ignores unknown keys.
 */
@Serializable
data class DocumentInviteDto(
    val id: String = "",
    val documentId: String? = null,
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

/** `GET /api/documents/{id}/invites` — documented as `{ "invites": [...] }`. */
@Serializable
data class DocumentInvitesResponse(
    val invites: List<DocumentInviteDto>? = null,
    val data: List<DocumentInviteDto>? = null,
) {
    val items: List<DocumentInviteDto> get() = invites ?: data ?: emptyList()
}

/**
 * `POST /api/documents/{id}/invites` — the documented 201 returns the invite fields
 * inline; a wrapped `{ "invite": ... }` / `{ "data": ... }` envelope (the convention
 * other create endpoints use) is accepted too.
 */
@Serializable
data class DocumentInviteEnvelope(
    val invite: DocumentInviteDto? = null,
    val data: DocumentInviteDto? = null,
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
    val inviteOrSelf: DocumentInviteDto?
        get() = invite ?: data ?: (email ?: url)?.let {
            DocumentInviteDto(
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
 * Body for `POST /api/documents/{id}/invites`. `role` defaults server-side to
 * `watcher`; `expiresAt` is optional (null == never expires) and is dropped by the
 * shared Json when null.
 */
@Serializable
data class CreateInviteRequest(
    val email: String,
    val role: String,
    val expiresAt: String? = null,
)
