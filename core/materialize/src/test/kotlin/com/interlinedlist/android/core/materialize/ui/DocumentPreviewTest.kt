package com.interlinedlist.android.core.materialize.ui

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.materialize.domain.DocumentListStyle
import com.interlinedlist.android.core.materialize.domain.ListColumnType
import com.interlinedlist.android.core.materialize.domain.MaterializeColumn
import com.interlinedlist.android.core.materialize.domain.RowDataStyle
import org.junit.Test

/**
 * The rendered document preview. It is built from the **source** columns, not
 * from the edited list columns: a document is derived by the server from the
 * source, so previewing renamed list columns here would promise something the
 * request does not carry.
 */
class DocumentPreviewTest {

    private val preview = MaterializePreview(
        suggestedTitle = "Books to Read",
        suggestedDescription = "My reading backlog.",
        columns = listOf(
            MaterializeColumn("title", "Title", ListColumnType.TEXT, sourceKey = "title"),
            MaterializeColumn("author", "Author", ListColumnType.TEXT, sourceKey = "author"),
            MaterializeColumn("year", "Year", ListColumnType.NUMBER, sourceKey = "year"),
        ),
        rows = listOf(
            MaterializePreviewRow(
                mapOf("title" to "The Dream Machine", "author" to "Waldrop", "year" to "2001"),
            ),
            MaterializePreviewRow(mapOf("title" to "Thinking in Systems", "author" to "Meadows")),
        ),
        totalRowCount = 340,
    )

    @Test
    fun `bulleted inline puts each row on one line with its fields appended`() {
        val rendered = renderDocumentPreview(
            title = "Books to Read",
            preview = preview,
            listStyle = DocumentListStyle.BULLETED,
            rowDataStyle = RowDataStyle.INLINE,
        )

        assertThat(rendered).contains("# Books to Read")
        assertThat(rendered).contains("- The Dream Machine — Author: Waldrop, Year: 2001")
        // A missing value is left out rather than rendered as an empty field.
        assertThat(rendered).contains("- Thinking in Systems — Author: Meadows")
    }

    @Test
    fun `numbered sub-items indents each field under its row`() {
        val rendered = renderDocumentPreview(
            title = "Books to Read",
            preview = preview,
            listStyle = DocumentListStyle.NUMBERED,
            rowDataStyle = RowDataStyle.SUB_ITEMS,
        )

        assertThat(rendered).contains("1. The Dream Machine")
        assertThat(rendered).contains("    - Author: Waldrop")
        assertThat(rendered).contains("    - Year: 2001")
        assertThat(rendered).contains("2. Thinking in Systems")
    }

    @Test
    fun `a truncated preview says how many more rows are coming`() {
        val rendered = renderDocumentPreview(
            title = "Books to Read",
            preview = preview,
            listStyle = DocumentListStyle.BULLETED,
            rowDataStyle = RowDataStyle.INLINE,
        )

        assertThat(rendered).contains("…and 338 more")
    }

    @Test
    fun `a source with no rows still previews its heading`() {
        val rendered = renderDocumentPreview(
            title = "Launch notes",
            preview = MaterializePreview(),
            listStyle = DocumentListStyle.BULLETED,
            rowDataStyle = RowDataStyle.INLINE,
        )

        assertThat(rendered).isEqualTo("# Launch notes")
    }

    @Test
    fun `a source that is already a document is previewed verbatim`() {
        val markdown = "# Launch notes\n\n- Ship it\n- Tell everyone\n"

        val rendered = renderDocumentPreview(
            title = "Copy of Launch notes",
            preview = preview.copy(documentMarkdown = markdown),
            listStyle = DocumentListStyle.NUMBERED,
            rowDataStyle = RowDataStyle.SUB_ITEMS,
        )

        // The rows and the edited title are ignored: this destination copies the
        // source document through rather than rendering a table as bullets.
        assertThat(rendered).isEqualTo("# Launch notes\n\n- Ship it\n- Tell everyone")
        assertThat(rendered).doesNotContain("The Dream Machine")
    }

    @Test
    fun `a blank verbatim markdown falls back to rendering the rows`() {
        val rendered = renderDocumentPreview(
            title = "Books to Read",
            preview = preview.copy(documentMarkdown = "   "),
            listStyle = DocumentListStyle.BULLETED,
            rowDataStyle = RowDataStyle.INLINE,
        )

        assertThat(rendered).contains("- The Dream Machine")
    }
}
