package com.interlinedlist.android.feature.lists.data

import com.interlinedlist.android.feature.lists.data.remote.dto.RowDto
import com.interlinedlist.android.feature.lists.domain.ListRow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Projects a dynamic row `data` object into a `key → display string` map.
 *
 * Rows are keyed by schema field keys but the client never assumes value types:
 * strings, numbers, booleans, nulls, and nested arrays/objects are all coerced to
 * a readable string so any schema renders in the generic table. Nulls become
 * empty strings so absent cells stay blank rather than showing "null".
 */
object RowMapper {

    fun fromDto(dto: RowDto): ListRow =
        ListRow(
            id = dto.id,
            values = dto.data.mapValues { (_, value) -> displayString(value) },
        )

    /** Coerces any JSON value to a human-readable string. */
    fun displayString(value: kotlinx.serialization.json.JsonElement): String = when (value) {
        is JsonNull -> ""
        is JsonPrimitive -> value.content
        is JsonArray -> value.joinToString(", ") { displayString(it) }
        is JsonObject -> value.entries.joinToString(", ") { (k, v) -> "$k: ${displayString(v)}" }
    }
}
