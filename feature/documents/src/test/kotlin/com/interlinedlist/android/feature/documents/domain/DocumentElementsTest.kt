package com.interlinedlist.android.feature.documents.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The two conversions "Create from…" performs on a document body:
 * heading/bullet → rows, and markdown → the plain text a post is drafted from.
 *
 * Both are pure functions, so they are pinned here rather than through the UI.
 */
class DocumentElementsTest {

    // --- Document -> list: each heading and bullet becomes its own row -------

    @Test
    fun `each heading and bullet point becomes its own row`() {
        val rows = documentListRows(
            """
            # Launch plan

            ## Week one
            - Draft the announcement
            - Line up the beta list

            ## Week two
            * Ship it
            """.trimIndent(),
        )

        assertThat(rows.map { it.text }).containsExactly(
            "Launch plan",
            "Week one",
            "Draft the announcement",
            "Line up the beta list",
            "Week two",
            "Ship it",
        ).inOrder()
        assertThat(rows.map { it.type }).containsExactly(
            DocumentElementType.HEADING,
            DocumentElementType.HEADING,
            DocumentElementType.LIST_ITEM,
            DocumentElementType.LIST_ITEM,
            DocumentElementType.HEADING,
            DocumentElementType.LIST_ITEM,
        ).inOrder()
    }

    @Test
    fun `a row remembers the heading it sits under`() {
        val rows = documentListRows("# Launch plan\n- Draft the announcement")

        assertThat(rows[0].section).isEqualTo("Launch plan")
        assertThat(rows[1].section).isEqualTo("Launch plan")
    }

    @Test
    fun `nested bullets each become a row and keep their depth`() {
        val rows = documentListRows(
            """
            - Fruit
              - Apples
                - Braeburn
            ${"\t"}- Tabbed
            """.trimIndent(),
        )

        assertThat(rows.map { it.text })
            .containsExactly("Fruit", "Apples", "Braeburn", "Tabbed").inOrder()
        // Two spaces of indentation is one level of nesting; a tab counts as two.
        assertThat(rows.map { it.level }).containsExactly(1, 2, 3, 2).inOrder()
    }

    @Test
    fun `mixed content keeps only the headings and bullets`() {
        val rows = documentListRows(
            """
            # Notes

            Some prose that is not a row.

            > A quote, also not a row.

            - A real row

            ```kotlin
            fun notARow() = Unit
            ```
            """.trimIndent(),
        )

        assertThat(rows.map { it.text }).containsExactly("Notes", "A real row").inOrder()
    }

    @Test
    fun `a document with neither headings nor bullets falls back to its blocks`() {
        val rows = documentListRows("First thought.\n\nSecond thought.")

        assertThat(rows.map { it.text }).containsExactly("First thought.", "Second thought.").inOrder()
        assertThat(rows.map { it.type })
            .containsExactly(DocumentElementType.PARAGRAPH, DocumentElementType.PARAGRAPH)
    }

    @Test
    fun `an empty document has no rows`() {
        assertThat(documentListRows("")).isEmpty()
        assertThat(documentListRows(null)).isEmpty()
        assertThat(documentListRows("   \n\n  ")).isEmpty()
    }

    @Test
    fun `consecutive prose lines are one block, not one per line`() {
        val elements = parseDocumentElements("A sentence\nwrapped over lines.\n\nA second block.")

        assertThat(elements.map { it.text })
            .containsExactly("A sentence wrapped over lines.", "A second block.").inOrder()
    }

    // --- Document -> message: plain text, paragraphs kept, code dropped -----

    @Test
    fun `paragraphs are preserved as blank-line separated blocks`() {
        val text = markdownToPlainText("# Title\n\nFirst paragraph.\n\nSecond paragraph.")

        assertThat(text).isEqualTo("Title\n\nFirst paragraph.\n\nSecond paragraph.")
    }

    @Test
    fun `a fenced code block is dropped`() {
        val text = markdownToPlainText(
            """
            Before the code.

            ```kotlin
            fun main() {
                println("hello")
            }
            ```

            After the code.
            """.trimIndent(),
        )

        assertThat(text).isEqualTo("Before the code.\n\nAfter the code.")
        assertThat(text).doesNotContain("println")
    }

    @Test
    fun `a tilde fenced code block is dropped`() {
        val text = markdownToPlainText("Before.\n\n~~~\nraw = 1\n~~~\n\nAfter.")

        assertThat(text).isEqualTo("Before.\n\nAfter.")
    }

    @Test
    fun `an unterminated fenced code block is dropped to the end`() {
        val text = markdownToPlainText("Before.\n\n```\nnever closed")

        assertThat(text).isEqualTo("Before.")
    }

    @Test
    fun `an indented code block is dropped`() {
        val text = markdownToPlainText(
            "Before the code.\n\n    fun main() {\n        println(\"hi\")\n    }\n\nAfter the code.",
        )

        assertThat(text).isEqualTo("Before the code.\n\nAfter the code.")
        assertThat(text).doesNotContain("println")
    }

    @Test
    fun `a document that is only code converts to nothing`() {
        assertThat(markdownToPlainText("```\nfun main() = Unit\n```")).isEmpty()
    }

    @Test
    fun `an indented bullet stays a bullet instead of becoming code`() {
        val text = markdownToPlainText("- Fruit\n    - Apples")

        assertThat(text).isEqualTo("• Fruit\n\n• Apples")
    }

    @Test
    fun `bullets keep a marker and quotes keep their text`() {
        val text = markdownToPlainText("- First\n- Second\n\n> Quoted thought")

        assertThat(text).isEqualTo("• First\n\n• Second\n\nQuoted thought")
    }

    @Test
    fun `inline markdown is flattened to its text`() {
        val text = markdownToPlainText(
            "A **bold** word, some _emphasis_, `code`, a [link](https://example.com) " +
                "and an image ![alt](https://example.com/a.png).",
        )

        assertThat(text).isEqualTo(
            "A bold word, some emphasis, code, a link and an image .",
        )
    }

    @Test
    fun `html and entities are resolved`() {
        val text = markdownToPlainText("<strong>Bold</strong> &amp; brave")

        assertThat(text).isEqualTo("Bold & brave")
    }

    @Test
    fun `an empty document converts to an empty string`() {
        assertThat(markdownToPlainText(null)).isEmpty()
        assertThat(markdownToPlainText("")).isEmpty()
    }
}
