package com.interlinedlist.android.feature.documents.ui.materialize

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.materialize.domain.MaterializeSource
import com.interlinedlist.android.core.materialize.domain.MaterializeTarget
import org.junit.Test

/**
 * What a document entry point hands the shared "Create from…" window.
 *
 * The source is the contract that matters — it is all that goes over the wire —
 * so it is asserted exactly; the preview is display material the window shows
 * before anything is created.
 */
class DocumentMaterializeLaunchTest {

    private val markdown = """
        # Launch plan

        Some prose.

        - Draft the announcement
        - Line up the beta list

        ```kotlin
        fun notARow() = Unit
        ```
    """.trimIndent()

    @Test
    fun `a whole document is an id-only document source`() {
        val launch = documentMaterializeLaunch(
            documentId = "doc_1234567890",
            title = "Launch plan",
            markdown = markdown,
            target = MaterializeTarget.LIST,
        )

        assertThat(launch.source).isEqualTo(MaterializeSource.Document("doc_1234567890"))
        assertThat(launch.source.kind).isEqualTo("document")
        assertThat(launch.initialTarget).isEqualTo(MaterializeTarget.LIST)
    }

    @Test
    fun `the preview maps each heading and bullet to a row`() {
        val launch = documentMaterializeLaunch(
            documentId = "doc_1",
            title = "Launch plan",
            markdown = markdown,
            target = MaterializeTarget.LIST,
        )
        val preview = launch.preview

        assertThat(preview.columns.map { it.propertyName })
            .containsExactly("Section", "Text", "Type").inOrder()
        // Every column takes its values from a source attribute the server
        // re-derives; none of them carries a client value.
        assertThat(preview.columns.map { it.sourceKey })
            .containsExactly("section", "text", "type").inOrder()
        assertThat(preview.rows.map { it.values["text"] })
            .containsExactly("Launch plan", "Draft the announcement", "Line up the beta list")
            .inOrder()
        assertThat(preview.rows.map { it.values["type"] })
            .containsExactly("heading", "list-item", "list-item").inOrder()
        assertThat(preview.rows[1].values["section"]).isEqualTo("Launch plan")
        assertThat(preview.totalRowCount).isEqualTo(3)
    }

    @Test
    fun `the preview carries the document verbatim and its plain-text draft`() {
        val preview = documentMaterializeLaunch(
            documentId = "doc_1",
            title = "Launch plan",
            markdown = markdown,
            target = MaterializeTarget.DOC,
        ).preview

        assertThat(preview.documentMarkdown).isEqualTo(markdown.trimEnd())
        // The message destination reads as plain text with the code block gone.
        assertThat(preview.draftBody).isEqualTo(
            "Launch plan\n\nSome prose.\n\n• Draft the announcement\n\n• Line up the beta list",
        )
    }

    @Test
    fun `a document destination suggests a copy rather than the same name`() {
        val toDoc = documentMaterializeLaunch("doc_12345678ab", "Launch plan", markdown, MaterializeTarget.DOC)
        val toList = documentMaterializeLaunch("doc_12345678ab", "Launch plan", markdown, MaterializeTarget.LIST)

        assertThat(toDoc.preview.suggestedTitle).isEqualTo("Copy of Launch plan")
        assertThat(toList.preview.suggestedTitle).isEqualTo("Launch plan")
        // The file name only applies to a document destination, so both carry it.
        assertThat(toDoc.preview.suggestedFileName).isEqualTo("copy-of-launch-plan-doc_1234.md")
        assertThat(toList.preview.suggestedFileName).isEqualTo("copy-of-launch-plan-doc_1234.md")
    }

    @Test
    fun `an untitled document still gets a usable title and file name`() {
        val preview = documentMaterializeLaunch("doc_1", "  ", null, MaterializeTarget.LIST).preview

        assertThat(preview.suggestedTitle).isEqualTo("Untitled")
        assertThat(preview.suggestedFileName).isEqualTo("copy-of-untitled-doc_1.md")
        assertThat(preview.rows).isEmpty()
        assertThat(preview.totalRowCount).isEqualTo(0)
    }

    @Test
    fun `a long document previews a window of rows but reports the true total`() {
        val long = (1..50).joinToString("\n") { "- Item $it" }

        val preview = documentMaterializeLaunch("doc_1", "Long", long, MaterializeTarget.LIST).preview

        assertThat(preview.rows).hasSize(PREVIEW_ROW_LIMIT)
        assertThat(preview.totalRowCount).isEqualTo(50)
        assertThat(preview.hiddenRowCount).isEqualTo(30)
    }

    @Test
    fun `a highlighted selection is a docElements source carrying the selection`() {
        val selection = "## Week one\n- Draft the announcement"

        val launch = documentSelectionMaterializeLaunch(
            documentId = "doc_1234567890",
            documentTitle = "Launch plan",
            selectedMarkdown = "\n$selection\n",
            target = MaterializeTarget.DOC,
        )

        // A selection has no id of its own: the document id is re-authorized
        // server-side and the markdown says which part of it was asked for.
        assertThat(launch.source)
            .isEqualTo(MaterializeSource.DocumentSelection("doc_1234567890", selection))
        assertThat(launch.source.kind).isEqualTo("docElements")
        assertThat(launch.initialTarget).isEqualTo(MaterializeTarget.DOC)
    }

    @Test
    fun `a selection previews only the rows inside it`() {
        val launch = documentSelectionMaterializeLaunch(
            documentId = "doc_1",
            documentTitle = "Launch plan",
            selectedMarkdown = "## Week one\n- Draft the announcement",
            target = MaterializeTarget.LIST,
        )

        assertThat(launch.preview.suggestedTitle).isEqualTo("Launch plan (selection)")
        assertThat(launch.preview.suggestedFileName).isEqualTo("launch-plan-selection.md")
        assertThat(launch.preview.rows.map { it.values["text"] })
            .containsExactly("Week one", "Draft the announcement").inOrder()
        assertThat(launch.preview.draftBody).isEqualTo("Week one\n\n• Draft the announcement")
    }

    @Test
    fun `slugs are file-name safe`() {
        assertThat(slugify("Launch plan: Q3 / 2026!")).isEqualTo("launch-plan-q3-2026")
        assertThat(slugify("   ")).isEqualTo("document")
    }
}
