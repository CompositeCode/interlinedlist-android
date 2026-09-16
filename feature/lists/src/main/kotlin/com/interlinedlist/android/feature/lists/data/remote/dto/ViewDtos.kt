package com.interlinedlist.android.feature.lists.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Wire models for the saved-views endpoints (`/api/lists/{id}/views`), captured
 * against the live API: a view is
 * `{ id, listId, userId, name, scope, config, isDefault, position }`.
 *
 * `config` is kept as a raw [JsonObject] rather than a typed struct so keys this
 * client does not model survive a round-trip; the server already drops what it
 * does not recognise and the client must not compound that.
 */
@Serializable
data class ListViewDto(
    val id: String = "",
    val listId: String? = null,
    val userId: String? = null,
    val name: String = "",
    val scope: String? = null,
    val config: JsonObject? = null,
    val isDefault: Boolean = false,
    val position: Int = 0,
)

/** Envelope for `GET /api/lists/{id}/views` — verified live shape uses `views`. */
@Serializable
data class ListViewsResponse(
    val views: List<ListViewDto>? = null,
    val data: List<ListViewDto>? = null,
) {
    val items: List<ListViewDto> get() = views ?: data ?: emptyList()
}

/**
 * Envelope for create / update / fork, all of which return `{ "view": { … } }`.
 * `data` and a bare object are tolerated for forward-compatibility.
 */
@Serializable
data class ListViewEnvelope(
    val view: ListViewDto? = null,
    val data: ListViewDto? = null,
    val id: String? = null,
    val listId: String? = null,
    val userId: String? = null,
    val name: String? = null,
    val scope: String? = null,
    val config: JsonObject? = null,
    val isDefault: Boolean = false,
    val position: Int = 0,
) {
    val viewOrSelf: ListViewDto?
        get() = view ?: data ?: id?.let {
            ListViewDto(
                id = it,
                listId = listId,
                userId = userId,
                name = name.orEmpty(),
                scope = scope,
                config = config,
                isDefault = isDefault,
                position = position,
            )
        }
}

/**
 * Body for `POST /api/lists/{id}/views`. `name` and `scope` are both required —
 * the server 400s with `scope must be "personal" or "shared"` otherwise — so the
 * repository validates before sending. Null optionals are dropped by the shared Json.
 */
@Serializable
data class CreateViewRequest(
    val name: String,
    val scope: String,
    val config: JsonObject? = null,
    val isDefault: Boolean? = null,
)

/** Body for `PUT /api/lists/{id}/views/{viewId}` — only the supplied fields change. */
@Serializable
data class UpdateViewRequest(
    val name: String? = null,
    val config: JsonObject? = null,
    val isDefault: Boolean? = null,
)

/** Response for `DELETE /api/lists/{id}/views/{viewId}` — `{ "message": "View deleted" }`. */
@Serializable
data class DeleteViewResponse(
    val message: String? = null,
)
