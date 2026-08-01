package com.interlinedlist.android.feature.profile.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Wire models for another user's public content — posts, lists, documents — plus the
 * user lookup and mutual-connections endpoints (Milestone L). Field names and
 * envelopes were confirmed against live GET calls; the shared [kotlinx.serialization.json.Json]
 * ignores unknown keys, so only the fields the read-only surfaces render are declared.
 */

/** A public post (message). Confirmed live: `id`, `content`, `createdAt` (+ nested author). */
@Serializable
data class PublicPostDto(
    val id: String,
    val content: String = "",
    val createdAt: String? = null,
)

/**
 * `GET /api/user/{username}/messages` (note the singular `user` segment — confirmed
 * against the OpenAPI spec and live). Posts arrive under `messages`, with `data` as a
 * defensive fallback.
 */
@Serializable
data class PublicPostsResponse(
    val messages: List<PublicPostDto>? = null,
    val data: List<PublicPostDto>? = null,
) {
    val posts: List<PublicPostDto> get() = messages ?: data ?: emptyList()
}

/** A public list summary. Confirmed live: `id`, `title`, `description`. */
@Serializable
data class PublicListDto(
    val id: String,
    val title: String = "",
    val description: String? = null,
)

/**
 * `GET /api/users/{username}/lists` (plural `users`). Confirmed live envelope:
 * `{ lists: [...], pagination }`; `data` is a defensive fallback.
 */
@Serializable
data class PublicListsResponse(
    val lists: List<PublicListDto>? = null,
    val data: List<PublicListDto>? = null,
) {
    val items: List<PublicListDto> get() = lists ?: data ?: emptyList()
}

/**
 * `GET /api/users/{username}/lists/{id}`. Confirmed live: the list is wrapped under
 * `list` (alongside `ancestors`); a bare/`data` shape is tolerated too.
 */
@Serializable
data class PublicListDetailResponse(
    val list: PublicListDto? = null,
    val data: PublicListDto? = null,
    val id: String? = null,
    val title: String? = null,
    val description: String? = null,
) {
    /** The list payload, whether wrapped under `list`/`data` or inlined at the top level. */
    val listOrSelf: PublicListDto?
        get() = list ?: data ?: id?.let {
            PublicListDto(id = it, title = title ?: "", description = description)
        }
}

/**
 * A single public list row. Confirmed live: the dynamic field map arrives under
 * `rowData` (not `data`, which the owner's own Lists module uses). Kept as a
 * [JsonObject] and projected to display strings by the mapper so any schema renders.
 */
@Serializable
data class PublicListRowDto(
    val id: String,
    val rowData: JsonObject = JsonObject(emptyMap()),
    val data: JsonObject? = null,
) {
    /** The field map from whichever key the server populated. */
    val fields: JsonObject get() = if (rowData.isNotEmpty()) rowData else (data ?: rowData)
}

/**
 * `GET /api/users/{username}/lists/{id}/data`. Confirmed live envelope:
 * `{ rows: [...], pagination }`; `data` is a defensive fallback.
 */
@Serializable
data class PublicListDataResponse(
    val rows: List<PublicListRowDto>? = null,
    val data: List<PublicListRowDto>? = null,
) {
    val items: List<PublicListRowDto> get() = rows ?: data ?: emptyList()
}

/** A public document summary. Confirmed live: `id`, `title`. */
@Serializable
data class PublicDocumentDto(
    val id: String,
    val title: String = "",
)

/**
 * `GET /api/users/{username}/documents`. Confirmed live envelope:
 * `{ documents: [...], folders }`; `data` is a defensive fallback.
 */
@Serializable
data class PublicDocumentsResponse(
    val documents: List<PublicDocumentDto>? = null,
    val data: List<PublicDocumentDto>? = null,
) {
    val items: List<PublicDocumentDto> get() = documents ?: data ?: emptyList()
}

/**
 * `GET /api/documents/{id}`. Confirmed live: the document (incl. `content`) is wrapped
 * under `document`; a bare/`data` shape is tolerated too.
 */
@Serializable
data class PublicDocumentDetailDto(
    val id: String,
    val title: String = "",
    val content: String = "",
)

@Serializable
data class PublicDocumentDetailResponse(
    val document: PublicDocumentDetailDto? = null,
    val data: PublicDocumentDetailDto? = null,
    val id: String? = null,
    val title: String? = null,
    val content: String? = null,
) {
    val documentOrSelf: PublicDocumentDetailDto?
        get() = document ?: data ?: id?.let {
            PublicDocumentDetailDto(id = it, title = title ?: "", content = content ?: "")
        }
}

/**
 * `GET /api/users/lookup?handle=...`. Confirmed live: a bare user object
 * (`{ id, username, displayName, avatar, isPrivate }`), so [UserLookupResponse] reuses
 * the top-level fields; some builds may wrap it under `user`.
 */
@Serializable
data class UserLookupResponse(
    val user: ProfileUserDto? = null,
    val id: String? = null,
    val username: String? = null,
    val displayName: String? = null,
    val avatar: String? = null,
    val avatarUrl: String? = null,
) {
    val userOrSelf: ProfileUserDto?
        get() = user ?: id?.let {
            ProfileUserDto(
                id = it,
                username = username ?: "",
                displayName = displayName,
                avatarUrl = avatarUrl,
                avatar = avatar,
            )
        }
}

/**
 * `GET /api/follow/{userId}/mutual`. Confirmed live: counts only —
 * `{ mutualFollowers, mutualFollowing }` (no user list). A couple of alias keys are
 * tolerated defensively.
 */
@Serializable
data class MutualConnectionsResponse(
    val mutualFollowers: Int? = null,
    val mutualFollowing: Int? = null,
    val followers: Int? = null,
    val following: Int? = null,
) {
    val mutualFollowersOrZero: Int get() = mutualFollowers ?: followers ?: 0
    val mutualFollowingOrZero: Int get() = mutualFollowing ?: following ?: 0
}

/** True when a JSON primitive holds a renderable, non-blank value. */
internal fun JsonPrimitive.isRenderable(): Boolean = content.isNotBlank()
