package com.interlinedlist.android.feature.lists.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Wire models for the Lists API. Field names follow the InterlinedList REST
 * contract; the shared [kotlinx.serialization.json.Json] is configured with
 * `ignoreUnknownKeys`, so extra server fields are tolerated and only the columns
 * we render need to be declared here.
 *
 * The list `schema` and each row's `data` are intentionally left as raw
 * [JsonElement]: they are user-defined and dynamic, so they are interpreted by
 * the mappers ([com.interlinedlist.android.feature.lists.data.SchemaMapper] /
 * [com.interlinedlist.android.feature.lists.data.RowMapper]) rather than by fixed
 * @Serializable shapes.
 */

/** A list envelope as returned by index and detail endpoints. */
@Serializable
data class ListDto(
    val id: String,
    val title: String = "",
    val description: String? = null,
    val itemCount: Int? = null,
    val rowCount: Int? = null,
    val count: Int? = null,
    val folderId: String? = null,
    val isPublic: Boolean = false,
    val updatedAt: String? = null,
    // Detail responses may inline the schema; the mapper handles either shape.
    val schema: JsonElement? = null,
)

/** Pagination block shared by list endpoints. */
@Serializable
data class PaginationDto(
    val total: Int = 0,
    val limit: Int = 0,
    val offset: Int = 0,
    val hasMore: Boolean = false,
)

/**
 * Envelope for `GET /api/lists` and `GET /api/lists/search`.
 * The payload may either be `{ data: [...], pagination: {...} }` or (for some
 * builds) `{ lists: [...] }`; both list keys are accepted.
 */
@Serializable
data class ListsResponse(
    val data: List<ListDto>? = null,
    val lists: List<ListDto>? = null,
    val pagination: PaginationDto? = null,
) {
    val items: List<ListDto> get() = data ?: lists ?: emptyList()
}

/** Envelope for `GET /api/lists/{id}`; the list may be wrapped or bare. */
@Serializable
data class ListEnvelope(
    val list: ListDto? = null,
    val data: ListDto? = null,
)

/** Body for `POST /api/lists`. `schema` is a serialised DSL string per the API. */
@Serializable
data class CreateListRequest(
    val title: String,
    val description: String? = null,
    val schema: String? = null,
    val isPublic: Boolean = false,
)

/** Body for `PUT /api/lists/{id}` — partial metadata updates. */
@Serializable
data class UpdateListRequest(
    val title: String? = null,
    val description: String? = null,
    val folderId: String? = null,
    val isPublic: Boolean? = null,
)
