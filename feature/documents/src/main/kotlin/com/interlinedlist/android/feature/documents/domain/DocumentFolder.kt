package com.interlinedlist.android.feature.documents.domain

/** A folder used to organise documents. Root folders have a null [parentId]. */
data class DocumentFolder(
    val id: String,
    val name: String,
    val parentId: String?,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

/**
 * A node in the client-side folder tree, built from the flat folder list (linked
 * by [DocumentFolder.parentId]) plus each folder's embedded documents. The root of
 * the tree is synthesised (see [FolderNode.ROOT_ID]) and holds the unfiled
 * documents returned by `GET /api/documents`.
 */
data class FolderNode(
    val id: String,
    val name: String,
    val parentId: String?,
    val children: List<FolderNode>,
    val documents: List<Document>,
) {
    /** True for the synthesised root node that holds unfiled documents. */
    val isRoot: Boolean get() = id == ROOT_ID

    companion object {
        /** Synthetic id for the root/"Documents" node (no real folder on the server). */
        const val ROOT_ID = "__root__"
        const val ROOT_NAME = "Documents"
    }
}

/**
 * The flattened, ready-to-render contents of a single folder: its direct
 * subfolders and its documents, plus the [breadcrumb] path from the root down to
 * (and including) this folder. Used by the browser UI at each drill-down level.
 */
data class FolderContents(
    val folderId: String,
    val folderName: String,
    val parentId: String?,
    val subfolders: List<FolderSummary>,
    val documents: List<Document>,
    val breadcrumb: List<FolderSummary>,
) {
    val isRoot: Boolean get() = folderId == FolderNode.ROOT_ID
    val isEmpty: Boolean get() = subfolders.isEmpty() && documents.isEmpty()
}

/** A lightweight folder reference for breadcrumbs, subfolder rows, and pickers. */
data class FolderSummary(
    val id: String,
    val name: String,
    val documentCount: Int = 0,
    val subfolderCount: Int = 0,
)
