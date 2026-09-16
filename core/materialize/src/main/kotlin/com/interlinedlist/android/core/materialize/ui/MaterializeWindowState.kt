package com.interlinedlist.android.core.materialize.ui

import com.interlinedlist.android.core.materialize.domain.DocConfig
import com.interlinedlist.android.core.materialize.domain.DocumentListStyle
import com.interlinedlist.android.core.materialize.domain.ListColumnType
import com.interlinedlist.android.core.materialize.domain.ListConfig
import com.interlinedlist.android.core.materialize.domain.MaterializeColumn
import com.interlinedlist.android.core.materialize.domain.MaterializeOutcome
import com.interlinedlist.android.core.materialize.domain.MaterializeRequest
import com.interlinedlist.android.core.materialize.domain.MaterializeSource
import com.interlinedlist.android.core.materialize.domain.MaterializeTarget
import com.interlinedlist.android.core.materialize.domain.MaterializedDocument
import com.interlinedlist.android.core.materialize.domain.MaterializedList
import com.interlinedlist.android.core.materialize.domain.MessageDraft
import com.interlinedlist.android.core.materialize.domain.RowDataStyle

/**
 * Everything the "Create from…" window needs to open, handed to it by whichever
 * surface started the flow.
 *
 * The window is deliberately entry-point agnostic: messages, lists, rows and
 * documents all open the same window, so it takes an id-only [source], the
 * destination the user picked from the menu, and a [preview] of what that
 * source looks like. It never reaches back into a feature module for data.
 */
data class MaterializeLaunch(
    val source: MaterializeSource,
    val initialTarget: MaterializeTarget = MaterializeTarget.LIST,
    val preview: MaterializePreview = MaterializePreview(),
)

/**
 * What the entry point already has on screen, so the window can show the rows
 * and columns that will be created **before** anything is saved.
 *
 * This is display material and a starting point for the editor, never a payload:
 * `POST /api/materialize` takes ids only and re-derives every cell server-side
 * (see `MaterializeSource`). Only [columns] leave the device, and only as a
 * schema — `propertyName`/`propertyType`/`sourceKey`, never a value.
 *
 * [totalRowCount] is the true size of the source even when [rows] holds only the
 * handful the window shows, which is what lets the preview say "340 items in
 * total" the way the web window does.
 */
data class MaterializePreview(
    val suggestedTitle: String = "",
    val suggestedDescription: String? = null,
    /** Suggested file name for a document destination; maps to `relativePath`. */
    val suggestedFileName: String? = null,
    val columns: List<MaterializeColumn> = emptyList(),
    val rows: List<MaterializePreviewRow> = emptyList(),
    val totalRowCount: Int = rows.size,
    /** The account's `defaultPubliclyVisible`, so the toggle opens where the user expects. */
    val defaultIsPublic: Boolean = false,
    /**
     * The markdown a `doc` destination copies through, when the entry point
     * already has it verbatim. A document source is copied, not rendered from
     * rows, so laying [rows] out as bullets would misdescribe what gets created.
     *
     * Display only, like the rest of this class: the server re-derives the real
     * document from its own copy of the source.
     */
    val documentMarkdown: String? = null,
    /**
     * The plain-text body a `message` destination would produce, when the entry
     * point can derive it — so the draft is visible before it is asked for.
     *
     * Display only, and deliberately **not** sent: `MaterializeRequest.ToMessageDraft`
     * carries no `content`, leaving the server to build the body and size it
     * against the account's own limit.
     */
    val draftBody: String? = null,
) {
    /** How many rows exist beyond the ones being shown. */
    val hiddenRowCount: Int get() = (totalRowCount - rows.size).coerceAtLeast(0)
}

/** One previewed row: display values keyed by the column's `propertyKey`. */
data class MaterializePreviewRow(val values: Map<String, String>)

/**
 * One column as the window edits it.
 *
 * [uiId] keys the row while the user types, so a rename or a removal stays on
 * the right column; it is never sent. [propertyKey] is null for a column the
 * user added — the key is derived from the name when the request is built —
 * and [sourceKey] is null for the same reason: a user-added column has no
 * source attribute to derive values from, so it is created empty.
 */
data class EditableColumn(
    val uiId: Long,
    val name: String,
    val type: ListColumnType = ListColumnType.TEXT,
    val sourceKey: String? = null,
    val propertyKey: String? = null,
) {
    /** A column with no name has nothing to create; it is dropped on confirm. */
    val isUsable: Boolean get() = name.isNotBlank()
}

/**
 * What a confirmed conversion produced, as the window renders it.
 *
 * Both navigation targets are exposed independently, because the `both`
 * destination creates two objects and the success state has to offer a link to
 * each. [draft] is the `message` destination's result — nothing was created.
 */
data class MaterializeSuccess(
    val list: MaterializedList? = null,
    val document: MaterializedDocument? = null,
    val draft: MessageDraft? = null,
) {
    val canOpenList: Boolean get() = list != null
    val canOpenDocument: Boolean get() = document != null
    val hasDraft: Boolean get() = draft != null
}

/** Folds an outcome into the navigation targets the success state offers. */
internal fun MaterializeOutcome.toSuccess(): MaterializeSuccess = when (this) {
    is MaterializeOutcome.ListCreated -> MaterializeSuccess(list = list)
    is MaterializeOutcome.DocumentCreated -> MaterializeSuccess(document = document)
    is MaterializeOutcome.ListAndDocumentCreated ->
        MaterializeSuccess(list = list, document = document)

    is MaterializeOutcome.DraftReady -> MaterializeSuccess(draft = draft)
}

/**
 * The window's state: one editing surface shared by all four destinations.
 *
 * Switching destination only changes [target] — nothing is thrown away, so an
 * edit that still applies is still there when the user comes back, while an edit
 * the new destination cannot use (a document has no columns) simply does not
 * reach the request. [toRequest] is the single place that decides which edits a
 * destination carries.
 *
 * [title] and [isPublic] are shared rather than duplicated per destination: the
 * user is naming and publishing one thing, and the `both` destination gives the
 * list and the document the same title, exactly as the published example does.
 */
data class MaterializeWindowUiState(
    val source: MaterializeSource,
    val target: MaterializeTarget,
    val preview: MaterializePreview,
    val title: String,
    val description: String,
    val isPublic: Boolean,
    val columns: List<EditableColumn>,
    val fileName: String,
    val listStyle: DocumentListStyle = DocumentListStyle.BULLETED,
    val rowDataStyle: RowDataStyle = RowDataStyle.INLINE,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val subscriptionRequired: Boolean = false,
    val success: MaterializeSuccess? = null,
) {

    /**
     * The destinations offered in this window. Message → message is the one
     * combination the product hides (Quote or Push covers it), so a messages
     * source does not offer it.
     */
    val availableTargets: List<MaterializeTarget>
        get() = MaterializeTarget.entries.filter {
            it != MaterializeTarget.MESSAGE || source !is MaterializeSource.Messages
        }

    val createsList: Boolean
        get() = target == MaterializeTarget.LIST || target == MaterializeTarget.BOTH

    val createsDocument: Boolean
        get() = target == MaterializeTarget.DOC || target == MaterializeTarget.BOTH

    val createsDraft: Boolean get() = target == MaterializeTarget.MESSAGE

    /**
     * The server validates the list title right after the target and answers
     * `A list title is required`, so the window refuses a blank one first. A
     * document title really is optional — omitted, the server derives one.
     */
    val titleError: String?
        get() = if (createsList && title.isBlank()) LIST_TITLE_REQUIRED else null

    val canConfirm: Boolean get() = !isSubmitting && success == null && titleError == null

    /** The row layout controls only bite when there is row data to lay out. */
    val showsRowLayoutOptions: Boolean get() = createsDocument && preview.rows.isNotEmpty()

    /** The rendered document preview, as the chosen styles would lay it out. */
    val documentPreview: String
        get() = renderDocumentPreview(title, preview, listStyle, rowDataStyle)

    companion object {
        const val LIST_TITLE_REQUIRED: String = "A list title is required"

        /** Opens the window on the entry point's suggestions. */
        fun from(launch: MaterializeLaunch): MaterializeWindowUiState = MaterializeWindowUiState(
            source = launch.source,
            target = launch.initialTarget,
            preview = launch.preview,
            title = launch.preview.suggestedTitle,
            description = launch.preview.suggestedDescription.orEmpty(),
            isPublic = launch.preview.defaultIsPublic,
            columns = launch.preview.columns.mapIndexed { index, column ->
                EditableColumn(
                    uiId = index.toLong(),
                    name = column.propertyName,
                    type = column.propertyType,
                    sourceKey = column.sourceKey,
                    propertyKey = column.propertyKey,
                )
            },
            fileName = launch.preview.suggestedFileName.orEmpty(),
        )
    }
}

/**
 * Projects the window onto the request its destination accepts.
 *
 * This is where "preserves the edits that still apply, discards the ones that
 * cannot" happens: the list edits go out only when a list is being created, the
 * document edits only when a document is, and the draft destination carries
 * neither because the server builds the body from the source itself.
 */
internal fun MaterializeWindowUiState.toRequest(): MaterializeRequest {
    require(titleError == null) { LIST_TITLE_REQUIRED_MESSAGE }
    return when (target) {
        MaterializeTarget.LIST -> MaterializeRequest.ToList(source, toListConfig())
        MaterializeTarget.DOC -> MaterializeRequest.ToDocument(source, toDocConfig())
        MaterializeTarget.BOTH ->
            MaterializeRequest.ToListAndDocument(source, toListConfig(), toDocConfig())

        MaterializeTarget.MESSAGE -> MaterializeRequest.ToMessageDraft(source)
    }
}

private const val LIST_TITLE_REQUIRED_MESSAGE =
    "A list destination cannot be confirmed without a title"

private fun MaterializeWindowUiState.toListConfig(): ListConfig = ListConfig(
    title = title.trim(),
    description = description.trim().takeIf { it.isNotBlank() },
    isPublic = isPublic,
    // No columns to speak of leaves the server's own derivation alone.
    fields = columns.filter { it.isUsable }.takeIf { it.isNotEmpty() }?.toColumns(),
)

private fun MaterializeWindowUiState.toDocConfig(): DocConfig = DocConfig(
    title = title.trim().takeIf { it.isNotBlank() },
    relativePath = fileName.trim().takeIf { it.isNotBlank() },
    isPublic = isPublic,
    listStyle = listStyle,
    rowDataStyle = rowDataStyle,
)

/**
 * Turns the edited rows into column definitions, deriving a `propertyKey` for
 * every user-added column and keeping those keys distinct — two columns sharing
 * a key would silently collapse into one.
 */
private fun List<EditableColumn>.toColumns(): List<MaterializeColumn> {
    val used = mutableSetOf<String>()
    return map { column ->
        val key = distinctKey(column.propertyKey ?: column.name.toPropertyKey(), used)
        used += key
        MaterializeColumn(
            propertyKey = key,
            propertyName = column.name.trim(),
            propertyType = column.type,
            sourceKey = column.sourceKey,
        )
    }
}

private fun distinctKey(candidate: String, used: Set<String>): String {
    if (candidate !in used) return candidate
    var suffix = 2
    while ("${candidate}_$suffix" in used) suffix++
    return "${candidate}_$suffix"
}

/** `Reading notes` → `reading_notes`; the schema keys the API accepts. */
private fun String.toPropertyKey(): String = trim()
    .lowercase()
    .map { if (it.isLetterOrDigit()) it else '_' }
    .joinToString("")
    .trim('_')
    .replace(Regex("_+"), "_")
    .ifBlank { "column" }
