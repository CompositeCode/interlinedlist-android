package com.interlinedlist.android.feature.documents.ui.materialize

import com.interlinedlist.android.core.materialize.domain.ListColumnType
import com.interlinedlist.android.core.materialize.domain.MaterializeColumn
import com.interlinedlist.android.core.materialize.domain.MaterializeSource
import com.interlinedlist.android.core.materialize.domain.MaterializeTarget
import com.interlinedlist.android.core.materialize.ui.MaterializeLaunch
import com.interlinedlist.android.core.materialize.ui.MaterializePreview
import com.interlinedlist.android.core.materialize.ui.MaterializePreviewRow
import com.interlinedlist.android.feature.documents.domain.DocumentElement
import com.interlinedlist.android.feature.documents.domain.documentListRows
import com.interlinedlist.android.feature.documents.domain.markdownToPlainText

/**
 * The columns a document source produces, in the order the list gets them.
 *
 * `sourceKey` is the contract that keeps this honest: it names the source
 * attribute the server re-derives each cell from — it never accepts cell values
 * from the client — so each column is pinned to an attribute a parsed document
 * element actually has (`section`, `text`, `type`), and the preview shows the
 * same values the server will fill in.
 */
internal val DOCUMENT_SOURCE_COLUMNS: List<MaterializeColumn> = listOf(
    MaterializeColumn("section", "Section", ListColumnType.TEXT, sourceKey = "section"),
    MaterializeColumn("text", "Text", ListColumnType.TEXTAREA, sourceKey = "text"),
    MaterializeColumn("type", "Type", ListColumnType.TEXT, sourceKey = "type"),
)

/** How many rows the window previews; [MaterializePreview.totalRowCount] keeps the real size. */
internal const val PREVIEW_ROW_LIMIT = 20

/**
 * Opens "Create from…" on a **whole document**.
 *
 * The request that eventually goes out carries nothing but the document id: the
 * server re-fetches and re-authorizes the document and derives the new list or
 * document from its own copy. Everything built here is preview material, which
 * is why it is safe to derive it from [markdown] the editor may not have saved
 * yet — a stale preview cannot produce a wrong creation.
 *
 * [target] is the destination the ＋ Create menu picked, and it shapes the
 * suggested title the same way the web window does: a document destination
 * suggests "Copy of …" so the copy is not indistinguishable from its original.
 */
fun documentMaterializeLaunch(
    documentId: String,
    title: String,
    markdown: String?,
    target: MaterializeTarget,
): MaterializeLaunch {
    val documentTitle = title.ifBlank { UNTITLED }
    val suggested = if (target == MaterializeTarget.DOC) "Copy of $documentTitle" else documentTitle
    return MaterializeLaunch(
        source = MaterializeSource.Document(documentId),
        initialTarget = target,
        preview = previewOf(
            suggestedTitle = suggested,
            markdown = markdown,
            fileName = "${slugify("Copy of $documentTitle")}-${documentId.take(ID_PREFIX)}.md",
        ),
    )
}

/**
 * Opens "Create from…" on a **highlighted passage** of a document.
 *
 * This is the one source with no id of its own, so the wire `docElements` kind
 * carries the document id *and* the selected markdown — the document is still
 * re-authorized server-side, and the selection is the part of it being asked
 * for. Callers must not offer the action for a blank selection; the domain type
 * rejects one.
 */
fun documentSelectionMaterializeLaunch(
    documentId: String,
    documentTitle: String,
    selectedMarkdown: String,
    target: MaterializeTarget,
): MaterializeLaunch {
    val selection = selectedMarkdown.trim()
    val suggested = "${documentTitle.ifBlank { UNTITLED }} (selection)"
    return MaterializeLaunch(
        source = MaterializeSource.DocumentSelection(documentId, selection),
        initialTarget = target,
        preview = previewOf(
            suggestedTitle = suggested,
            markdown = selection,
            fileName = "${slugify(suggested)}.md",
        ),
    )
}

/**
 * The preview both document entry points show: one row per heading and bullet,
 * the markdown a document destination would copy through, and the plain text a
 * message destination reads as.
 */
private fun previewOf(
    suggestedTitle: String,
    markdown: String?,
    fileName: String,
): MaterializePreview {
    val rows = documentListRows(markdown)
    return MaterializePreview(
        suggestedTitle = suggestedTitle,
        suggestedFileName = fileName,
        columns = DOCUMENT_SOURCE_COLUMNS,
        rows = rows.take(PREVIEW_ROW_LIMIT).map { it.toPreviewRow() },
        totalRowCount = rows.size,
        documentMarkdown = markdown?.trimEnd(),
        draftBody = markdownToPlainText(markdown),
    )
}

/** Values keyed by the column each one is derived from server-side. */
private fun DocumentElement.toPreviewRow() = MaterializePreviewRow(
    mapOf(
        "section" to section,
        "text" to text,
        "type" to type.apiValue,
    ),
)

/** `Launch plan` → `launch-plan`; the file-name shape `relativePath` expects. */
internal fun slugify(title: String): String = title
    .trim()
    .lowercase()
    .replace(Regex("\\s+"), "-")
    .replace(Regex("[^a-z0-9\\-_]+"), "-")
    .replace(Regex("-+"), "-")
    .trim('-')
    .ifBlank { "document" }

private const val UNTITLED = "Untitled"

/** How much of the document id disambiguates the file name. */
private const val ID_PREFIX = 8
