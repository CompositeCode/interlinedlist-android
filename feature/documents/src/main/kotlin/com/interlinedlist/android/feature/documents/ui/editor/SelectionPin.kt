package com.interlinedlist.android.feature.documents.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Which passage the editor's Selection action is offering, as the selection
 * changes.
 *
 * A fresh highlight always wins. A *collapsed* selection usually means the user
 * deselected — but tapping the Selection button moves focus off the body field,
 * and a text field collapses its highlight to the end of the range when it loses
 * focus. Dropping the passage there would take the action away at the exact
 * moment it was asked for, so a caret that lands **inside** the pinned passage
 * keeps it. A caret anywhere else, or an edit, drops it.
 *
 * Pure so the rule is unit-testable; the editor only feeds it selections.
 */
internal fun pinnedSelection(
    previous: TextRange?,
    value: TextFieldValue,
    textChanged: Boolean,
): TextRange? = when {
    textChanged -> null
    !value.selection.collapsed -> value.selection
    previous != null && value.selection.start in previous.min..previous.max -> previous
    else -> null
}

/** The text [range] covers, clamped so a stale range can never throw. */
internal fun TextFieldValue.textIn(range: TextRange?): String {
    if (range == null) return ""
    val start = range.min.coerceIn(0, text.length)
    val end = range.max.coerceIn(start, text.length)
    return text.substring(start, end)
}
