package com.interlinedlist.android.feature.lists.domain

/**
 * The user-defined shape of a list. A list's rows are dynamic key→value maps; the
 * [fields] here describe which keys exist, how to label them, and how to render
 * and edit their values. The UI renders columns and the add/edit form generically
 * from these fields — nothing about a list's columns is hardcoded.
 */
data class ListSchema(
    val fields: List<SchemaField>,
) {
    val isEmpty: Boolean get() = fields.isEmpty()

    companion object {
        val EMPTY = ListSchema(emptyList())
    }
}

/**
 * A single column/property in a list's schema.
 *
 * @param key the stable key used in a row's data map (e.g. `"name"`).
 * @param label human-friendly column header; falls back to [key] when absent.
 * @param type controls rendering and the form input used to edit the value.
 * @param required whether the add/edit form should treat the field as mandatory.
 * @param options selectable values for [FieldType.SELECT] fields.
 */
data class SchemaField(
    val key: String,
    val label: String,
    val type: FieldType,
    val required: Boolean = false,
    val options: List<String> = emptyList(),
)

/**
 * Supported schema field types. Unknown/absent DSL types map to [TEXT] so the row
 * still renders and stays editable rather than being dropped.
 */
enum class FieldType {
    TEXT,
    NUMBER,
    BOOLEAN,
    DATE,
    URL,
    SELECT;

    companion object {
        /** Maps a DSL type string (case-insensitive) to a [FieldType], defaulting to [TEXT]. */
        fun fromDsl(raw: String?): FieldType = when (raw?.trim()?.lowercase()) {
            "number", "int", "integer", "float", "decimal" -> NUMBER
            "boolean", "bool", "checkbox" -> BOOLEAN
            "date", "datetime", "timestamp" -> DATE
            "url", "link" -> URL
            "select", "enum", "option", "options" -> SELECT
            else -> TEXT
        }
    }
}
