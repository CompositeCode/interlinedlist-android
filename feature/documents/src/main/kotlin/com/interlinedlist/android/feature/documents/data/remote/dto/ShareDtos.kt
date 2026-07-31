package com.interlinedlist.android.feature.documents.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Wire models for the document sharing endpoints (see the `DocumentShareLink`
 * schema). The shared Json ignores unknown keys and coerces invalid values to
 * defaults, so all optionals are defaulted and only rendered fields are declared.
 */
@Serializable
data class ShareLinkDto(
    val id: String = "",
    val documentId: String? = null,
    val token: String = "",
    val role: String? = null,
    val expiresAt: String? = null,
    val revokedAt: String? = null,
    val createdAt: String? = null,
)

/** Envelope for `GET /api/documents/{id}/share-links` (verified live: `shareLinks`). */
@Serializable
data class ShareLinksResponse(
    val shareLinks: List<ShareLinkDto>? = null,
    val data: List<ShareLinkDto>? = null,
) {
    val items: List<ShareLinkDto> get() = shareLinks ?: data ?: emptyList()
}

/**
 * Envelope for `POST /api/documents/{id}/share-links`; the created link may be
 * wrapped under `shareLink`/`data` or returned bare.
 */
@Serializable
data class ShareLinkEnvelope(
    val shareLink: ShareLinkDto? = null,
    val data: ShareLinkDto? = null,
    val id: String? = null,
    val token: String? = null,
    val role: String? = null,
    val expiresAt: String? = null,
    val revokedAt: String? = null,
    val createdAt: String? = null,
) {
    val linkOrSelf: ShareLinkDto?
        get() = shareLink ?: data ?: token?.let {
            ShareLinkDto(
                id = id.orEmpty(),
                token = it,
                role = role,
                expiresAt = expiresAt,
                revokedAt = revokedAt,
                createdAt = createdAt,
            )
        }
}

/**
 * Body for `POST /api/documents/{id}/share-links`. The spec models only `expiresAt`,
 * but the link entity carries a `role`; we send both so the chosen access level is
 * honoured. Nulls are dropped by the shared Json.
 */
@Serializable
data class CreateShareLinkRequest(
    val role: String? = null,
    val expiresAt: String? = null,
)

/** A nested owner reference on a shared document response. */
@Serializable
data class ShareUserDto(
    val id: String = "",
    val username: String = "",
    val displayName: String? = null,
)

/**
 * Response for `GET /api/documents/shared/{token}` — resolves a public link to a
 * read-only preview. The document may be inlined or wrapped under `document`; the
 * `role` is the access the link grants.
 */
@Serializable
data class SharedDocumentResponse(
    val document: DocumentDto? = null,
    val id: String? = null,
    val title: String? = null,
    val content: String? = null,
    val role: String? = null,
    val user: ShareUserDto? = null,
) {
    /** The resolved document metadata, whether wrapped under `document` or inlined. */
    val documentOrSelf: DocumentDto?
        get() = document ?: id?.let {
            DocumentDto(id = it, title = title, content = content)
        }
}
