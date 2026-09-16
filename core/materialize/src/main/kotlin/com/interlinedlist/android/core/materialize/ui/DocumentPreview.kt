package com.interlinedlist.android.core.materialize.ui

import com.interlinedlist.android.core.materialize.domain.DocumentListStyle
import com.interlinedlist.android.core.materialize.domain.RowDataStyle

/**
 * Renders the markdown a document destination would produce, so the window can
 * show it before anything is written.
 *
 * It renders the **source** columns from [preview], not the edited list columns:
 * a document is derived server-side from the source, and `docConfig` carries no
 * field list, so showing renamed list columns here would promise something the
 * request does not ask for.
 *
 * The result is a preview, not the document: the server re-derives the real one
 * from its own copy of the source.
 */
internal fun renderDocumentPreview(
    title: String,
    preview: MaterializePreview,
    listStyle: DocumentListStyle,
    rowDataStyle: RowDataStyle,
): String = buildString {
    if (title.isNotBlank()) appendLine("# ${title.trim()}").appendLine()
    preview.suggestedDescription?.takeIf { it.isNotBlank() }?.let {
        appendLine(it.trim()).appendLine()
    }

    val headlineKey = preview.columns.firstOrNull()?.propertyKey
    preview.rows.forEachIndexed { index, row ->
        val marker = when (listStyle) {
            DocumentListStyle.NUMBERED -> "${index + 1}. "
            DocumentListStyle.BULLETED -> "- "
        }
        val headline = headlineKey?.let { row.values[it] }?.takeIf { it.isNotBlank() } ?: UNTITLED_ROW
        val details = preview.columns.drop(1).mapNotNull { column ->
            row.values[column.propertyKey]
                ?.takeIf { it.isNotBlank() }
                ?.let { column.propertyName to it }
        }

        when (rowDataStyle) {
            RowDataStyle.INLINE -> {
                append(marker).append(headline)
                if (details.isNotEmpty()) {
                    append(" — ").append(details.joinToString(", ") { "${it.first}: ${it.second}" })
                }
                appendLine()
            }

            RowDataStyle.SUB_ITEMS -> {
                appendLine("$marker$headline")
                details.forEach { appendLine("$SUB_ITEM_INDENT- ${it.first}: ${it.second}") }
            }
        }
    }

    if (preview.hiddenRowCount > 0) {
        appendLine().append("…and ${preview.hiddenRowCount} more")
    }
}.trimEnd()

private const val UNTITLED_ROW = "(untitled)"
private const val SUB_ITEM_INDENT = "    "
