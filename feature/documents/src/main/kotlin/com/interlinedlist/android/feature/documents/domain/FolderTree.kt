package com.interlinedlist.android.feature.documents.domain

/**
 * Pure, testable builders that turn the flat folder list (linked by
 * [DocumentFolder.parentId]) plus per-folder documents and the unfiled root
 * documents into a [FolderNode] tree and, from any point in that tree, into the
 * [FolderContents] the browser renders.
 *
 * These functions do no I/O; the repository feeds them cached folders/documents
 * so the tree can be rebuilt reactively from Room.
 */
object FolderTree {

    /**
     * Builds the folder tree.
     *
     * @param folders every known folder (any order); root folders have a null parentId.
     * @param documentsByFolder documents keyed by their folder id (embedded folder docs).
     * @param rootDocuments unfiled documents (folderId == null), hung off the synthetic root.
     *
     * Orphan folders (whose parentId points at an unknown/deleted folder) are
     * re-parented onto the root so they remain reachable rather than lost.
     */
    fun build(
        folders: List<DocumentFolder>,
        documentsByFolder: Map<String, List<Document>>,
        rootDocuments: List<Document>,
    ): FolderNode {
        val knownIds = folders.mapTo(HashSet()) { it.id }
        // Group children by their effective parent (null / unknown parent => root).
        val childrenByParent: Map<String?, List<DocumentFolder>> = folders
            .sortedBy { it.name.lowercase() }
            .groupBy { folder ->
                folder.parentId?.takeIf { it in knownIds }
            }

        fun buildNode(folder: DocumentFolder): FolderNode = FolderNode(
            id = folder.id,
            name = folder.name,
            parentId = folder.parentId?.takeIf { it in knownIds },
            children = childrenByParent[folder.id].orEmpty().map(::buildNode),
            documents = documentsByFolder[folder.id].orEmpty(),
        )

        val topLevel = childrenByParent[null].orEmpty().map(::buildNode)
        return FolderNode(
            id = FolderNode.ROOT_ID,
            name = FolderNode.ROOT_NAME,
            parentId = null,
            children = topLevel,
            documents = rootDocuments,
        )
    }

    /**
     * Resolves the contents to show for [folderId] within [root]. A null or the
     * synthetic [FolderNode.ROOT_ID] resolves to the root itself. If the id is not
     * found (e.g. a folder was deleted while its route was open), falls back to the
     * root so the UI degrades gracefully.
     */
    fun contentsOf(root: FolderNode, folderId: String?): FolderContents {
        val targetId = folderId ?: FolderNode.ROOT_ID
        val path = pathTo(root, targetId) ?: listOf(root)
        val node = path.last()
        return FolderContents(
            folderId = node.id,
            folderName = node.name,
            parentId = node.parentId,
            subfolders = node.children.map { it.toSummary() },
            documents = node.documents,
            breadcrumb = path.map { it.toSummary() },
        )
    }

    /** Returns the path of nodes from [root] down to the node with [targetId], or null. */
    private fun pathTo(root: FolderNode, targetId: String): List<FolderNode>? {
        if (root.id == targetId) return listOf(root)
        for (child in root.children) {
            val sub = pathTo(child, targetId)
            if (sub != null) return listOf(root) + sub
        }
        return null
    }

    private fun FolderNode.toSummary() = FolderSummary(
        id = id,
        name = name,
        documentCount = documents.size,
        subfolderCount = children.size,
    )
}
