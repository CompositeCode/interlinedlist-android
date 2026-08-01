package com.interlinedlist.android.feature.lists.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Wire models for the list sharing endpoints. Field names follow the InterlinedList
 * REST contract (see the `ListShareLink` schema); the shared Json ignores unknown
 * keys, so only the fields the UI renders are declared. All optionals are defaulted
 * so the shared `coerceInputValues` Json never fails on explicit nulls.
 */
@Serializable
data class ShareLinkDto(
    val id: String = "",
    val listId: String? = null,
    val token: String = "",
    val role: String? = null,
    val expiresAt: String? = null,
    val revokedAt: String? = null,
    val createdAt: String? = null,
)

/**
 * Envelope for `GET /api/lists/{id}/share-links`. Links arrive under `shareLinks`
 * (verified live) but `data` is tolerated for forward-compatibility.
 */
@Serializable
data class ShareLinksResponse(
    val shareLinks: List<ShareLinkDto>? = null,
    val data: List<ShareLinkDto>? = null,
) {
    val items: List<ShareLinkDto> get() = shareLinks ?: data ?: emptyList()
}

/**
 * Envelope for `POST /api/lists/{id}/share-links`. The created link may be returned
 * bare or wrapped under `shareLink`/`data`; [linkOrSelf] resolves whichever form.
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
 * Body for `POST /api/lists/{id}/share-links`. The spec models only `expiresAt`,
 * but the link entity carries a `role`; we send both so the chosen access level is
 * honoured (server defaults it when omitted). Nulls are dropped by the shared Json.
 */
@Serializable
data class CreateShareLinkRequest(
    val role: String? = null,
    val expiresAt: String? = null,
)

/** A nested owner reference on a watched list row. */
@Serializable
data class ShareUserDto(
    val id: String = "",
    val username: String = "",
    val displayName: String? = null,
)

/**
 * One row of `GET /api/lists/watching` — a full list plus the owning `user` and the
 * `role` the current user holds. Only the fields the "Shared with me" list renders
 * are declared; the rest are ignored.
 */
@Serializable
data class WatchingListDto(
    val id: String,
    val title: String = "",
    val description: String? = null,
    val isPublic: Boolean = false,
    val role: String? = null,
    val user: ShareUserDto? = null,
)

/** Envelope for `GET /api/lists/watching` — verified live shape uses `lists`. */
@Serializable
data class WatchingResponse(
    val lists: List<WatchingListDto>? = null,
    val data: List<WatchingListDto>? = null,
) {
    val items: List<WatchingListDto> get() = lists ?: data ?: emptyList()
}

/**
 * Response for `GET /api/lists/shared/{token}` — resolves a public link to a
 * read-only preview. The payload may inline the list fields or wrap them under
 * `list`; the `role` is the access the link grants. Rows may accompany the preview.
 */
@Serializable
data class SharedListResponse(
    val list: WatchingListDto? = null,
    val id: String? = null,
    val title: String? = null,
    val description: String? = null,
    val isPublic: Boolean = false,
    val role: String? = null,
    val user: ShareUserDto? = null,
    val rows: List<RowDto>? = null,
    val data: List<RowDto>? = null,
) {
    /** The resolved list metadata, whether wrapped under `list` or inlined. */
    val listOrSelf: WatchingListDto?
        get() = list ?: id?.let {
            WatchingListDto(
                id = it,
                title = title.orEmpty(),
                description = description,
                isPublic = isPublic,
                role = role,
                user = user,
            )
        }

    /** Preview rows, tolerating either `rows` or `data`. */
    val rowsOrEmpty: List<RowDto> get() = rows ?: data ?: emptyList()
}
