package com.interlinedlist.android.feature.lists.domain

/**
 * One data row of a list. [values] is a dynamic map keyed by [SchemaField.key];
 * values are normalised to display strings so the table can render any schema
 * without knowing field types at compile time. The raw typed access lives in the
 * mapper — the UI only needs the string projection to show and edit cells.
 */
data class ListRow(
    val id: String,
    val values: Map<String, String>,
    /**
     * The row's server-side version counter, when the source versions rows. It is
     * what the freshness poll quotes to ask "has this row moved?", so a row with a
     * null version is simply left out of that question rather than guessed at.
     */
    val version: Int? = null,
) {
    /** Value for [key], or empty string when the row omits that field. */
    fun valueFor(key: String): String = values[key].orEmpty()
}

/** A full list ready for the detail screen: metadata + schema + the loaded rows. */
data class ListDetail(
    val summary: ListSummary,
    val schema: ListSchema,
    val rows: List<ListRow>,
)
