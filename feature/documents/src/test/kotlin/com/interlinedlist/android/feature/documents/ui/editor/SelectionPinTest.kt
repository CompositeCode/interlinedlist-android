package com.interlinedlist.android.feature.documents.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The rule that decides which passage the editor's Selection action offers. */
class SelectionPinTest {

    private val body = TextFieldValue("# Launch plan\n\n## Week one\n- Ship it")

    @Test
    fun `a highlight becomes the selection`() {
        val pinned = pinnedSelection(
            previous = null,
            value = body.copy(selection = TextRange(15, 26)),
            textChanged = false,
        )

        assertThat(pinned).isEqualTo(TextRange(15, 26))
    }

    @Test
    fun `a new highlight replaces the previous one`() {
        val pinned = pinnedSelection(
            previous = TextRange(0, 13),
            value = body.copy(selection = TextRange(15, 26)),
            textChanged = false,
        )

        assertThat(pinned).isEqualTo(TextRange(15, 26))
    }

    @Test
    fun `a caret collapsing inside the passage keeps it`() {
        // Blurring the field to tap the Selection menu collapses the highlight
        // to its end; the action must still be about what was highlighted.
        val pinned = pinnedSelection(
            previous = TextRange(15, 26),
            value = body.copy(selection = TextRange(26)),
            textChanged = false,
        )

        assertThat(pinned).isEqualTo(TextRange(15, 26))
    }

    @Test
    fun `a caret moved elsewhere drops the passage`() {
        val pinned = pinnedSelection(
            previous = TextRange(15, 26),
            value = body.copy(selection = TextRange(2)),
            textChanged = false,
        )

        assertThat(pinned).isNull()
    }

    @Test
    fun `editing the text drops the passage`() {
        val pinned = pinnedSelection(
            previous = TextRange(15, 26),
            value = TextFieldValue("edited", TextRange(15, 26)),
            textChanged = true,
        )

        assertThat(pinned).isNull()
    }

    @Test
    fun `the pinned passage reads back as the highlighted markdown`() {
        assertThat(body.textIn(TextRange(15, 26))).isEqualTo("## Week one")
        assertThat(body.textIn(null)).isEmpty()
        // A range that outlived its text is clamped rather than thrown.
        assertThat(TextFieldValue("short").textIn(TextRange(2, 99))).isEqualTo("ort")
    }
}
