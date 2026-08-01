package com.interlinedlist.android.feature.lists.data

import com.interlinedlist.android.feature.lists.domain.FieldType
import com.interlinedlist.android.feature.lists.domain.ListSchema
import com.interlinedlist.android.feature.lists.domain.SchemaField
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * Interprets a list's user-defined schema DSL into a typed [ListSchema].
 *
 * The DSL is dynamic and reaches the client in a few shapes across API versions;
 * this mapper accepts all of them so column rendering never depends on one exact
 * wire form:
 *
 *  1. An **array** of field objects: `[{ "key": "name", "type": "text", ... }]`
 *  2. An **object** keyed by field key: `{ "name": { "type": "text" }, ... }`
 *  3. A **wrapper object** whose `properties`/`fields` holds one of the above.
 *
 * Within a field object, the key may be under `key`/`name`/`id`, the label under
 * `label`/`title`/`name`, and the type under `type`/`fieldType`. Unknown types
 * fall back to [FieldType.TEXT] (see [FieldType.fromDsl]) so no column is lost.
 */
object SchemaMapper {

    /** The DSL wire value for each [FieldType]; the inverse of [FieldType.fromDsl]. */
    private fun FieldType.toDsl(): String = when (this) {
        FieldType.TEXT -> "text"
        FieldType.NUMBER -> "number"
        FieldType.BOOLEAN -> "boolean"
        FieldType.DATE -> "date"
        FieldType.URL -> "url"
        FieldType.SELECT -> "select"
    }

    /**
     * Serialises a [ListSchema] back to the canonical array DSL the API accepts on
     * `PUT /api/lists/{id}/schema`: `[{ "key", "label", "type", "required"?,
     * "options"? }, ...]`. Blank keys are dropped so an empty editor row is not
     * persisted; [required]/[options] are only emitted when meaningful.
     */
    fun toDsl(schema: ListSchema): JsonArray = buildJsonArray {
        schema.fields
            .filter { it.key.isNotBlank() }
            .forEach { field ->
                add(
                    buildJsonObject {
                        put("key", field.key)
                        put("label", field.label)
                        put("type", field.type.toDsl())
                        if (field.required) put("required", true)
                        if (field.options.isNotEmpty()) {
                            put(
                                "options",
                                buildJsonArray { field.options.forEach { add(JsonPrimitive(it)) } },
                            )
                        }
                    },
                )
            }
    }

    /** Parses the (possibly null) schema element; returns [ListSchema.EMPTY] if unusable. */
    fun fromJson(element: JsonElement?): ListSchema {
        val root = unwrap(element) ?: return ListSchema.EMPTY
        val fields = when (root) {
            is JsonArray -> root.mapNotNull { fieldFromObject(it, keyHint = null) }
            is JsonObject -> root.entries.mapNotNull { (key, value) ->
                fieldFromObject(value, keyHint = key)
            }
            else -> emptyList()
        }
        return ListSchema(fields)
    }

    /** Unwraps a `{ properties: ... }` / `{ fields: ... }` container to its payload. */
    private fun unwrap(element: JsonElement?): JsonElement? {
        // A bare primitive can't describe a schema.
        if (element == null || element is JsonPrimitive) return null
        val obj = element as? JsonObject ?: return element
        // A container object exposes the actual field set under a known key.
        (obj["properties"] ?: obj["fields"] ?: obj["schema"] ?: obj["columns"])?.let {
            return it
        }
        return obj
    }

    /**
     * Builds a [SchemaField] from a value that is either a field-descriptor object
     * or (when the schema is a bare object of key→type strings) a type primitive.
     */
    private fun fieldFromObject(value: JsonElement, keyHint: String?): SchemaField? {
        // Shape: { "name": "text" } — the value is just the type string.
        if (value is JsonPrimitive) {
            val key = keyHint ?: return null
            return SchemaField(
                key = key,
                label = humanize(key),
                type = FieldType.fromDsl(value.contentOrNull),
            )
        }

        val obj = value as? JsonObject ?: return null
        val key = keyHint
            ?: obj.string("key")
            ?: obj.string("name")
            ?: obj.string("id")
            ?: return null

        val label = obj.string("label")
            ?: obj.string("title")
            ?: obj.string("name")?.takeIf { keyHint != null }
            ?: humanize(key)

        val type = FieldType.fromDsl(obj.string("type") ?: obj.string("fieldType"))
        val required = obj["required"]?.let { (it as? JsonPrimitive)?.booleanOrNull } ?: false
        val options = (obj["options"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?: emptyList()

        return SchemaField(
            key = key,
            label = label,
            type = type,
            required = required,
            options = options,
        )
    }

    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    /** Turns a raw key like `first_name`/`firstName` into a readable `First Name`. */
    private fun humanize(key: String): String {
        val spaced = key
            .replace('_', ' ')
            .replace('-', ' ')
            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .trim()
        return spaced.split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
            .ifBlank { key }
    }
}
