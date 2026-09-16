package com.interlinedlist.android.feature.lists.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * A single data row. The field map is a dynamic key→value object keyed by schema
 * field keys; it is kept as a [JsonObject] and projected to display strings by
 * the mapper, so any schema is supported without a fixed shape.
 *
 * The live API sends it as **`rowData`** (confirmed against `GET .../data`, the
 * create/update echo and the freshness poll), while `data` is accepted as well
 * since some payloads have been modelled that way; [fields] picks whichever is
 * present.
 *
 * [version] is the row's optimistic-concurrency counter — what the grid's
 * freshness poll compares against. It is absent on sources that do not version
 * rows (a GitHub-backed list).
 */
@Serializable
data class RowDto(
    val id: String,
    val rowData: JsonObject? = null,
    val data: JsonObject? = null,
    val version: Int? = null,
) {
    val fields: JsonObject get() = rowData ?: data ?: JsonObject(emptyMap())
}

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
