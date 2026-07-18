package com.interlinedlist.android.feature.lists.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * A single data row. `data` is the dynamic key→value map keyed by schema field
 * keys; it is kept as a [JsonObject] and projected to display strings by the
 * mapper, so any schema is supported without a fixed shape.
 */
@Serializable
data class RowDto(
    val id: String,
    val data: JsonObject = JsonObject(emptyMap()),
)

/** Envelope for `GET /api/lists/{id}/data`. */
@Serializable
data class RowsResponse(
    val data: List<RowDto>? = null,
    val rows: List<RowDto>? = null,
    val pagination: PaginationDto? = null,
) {
    val items: List<RowDto> get() = data ?: rows ?: emptyList()
}

/** Envelope for a single-row create/get; the row may be wrapped or bare. */
@Serializable
data class RowEnvelope(
    val row: RowDto? = null,
    val data: RowDto? = null,
)

/**
 * Body for `POST`/`PUT` of a row. Per the API the `data` property carries the
 * row's field map (as a nested JSON object).
 */
@Serializable
data class RowWriteRequest(
    val data: Map<String, JsonElement>,
)
