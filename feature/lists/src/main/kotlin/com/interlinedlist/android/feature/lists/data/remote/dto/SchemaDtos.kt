package com.interlinedlist.android.feature.lists.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Body for `PUT /api/lists/{id}/schema`. The API accepts the schema as a
 * serialised DSL string (the same form `POST /api/lists` uses), so the editor
 * serialises the edited field list to a JSON array string via
 * [com.interlinedlist.android.feature.lists.data.SchemaMapper.toDsl] and sends it
 * here.
 */
@Serializable
data class UpdateSchemaRequest(
    val schema: String,
)

/**
 * Envelope for `PUT`/`GET` of a list's schema. The updated schema may come back
 * bare (an array/object) or wrapped under `schema`; the mapper interprets either
 * shape, so this keeps the payload as a raw [JsonElement].
 */
@Serializable
data class SchemaEnvelope(
    val schema: JsonElement? = null,
    val data: JsonElement? = null,
)
