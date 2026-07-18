package com.interlinedlist.android.feature.documents.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FolderTreeTest {

    private fun folder(id: String, name: String, parentId: String? = null) =
        DocumentFolder(id = id, name = name, parentId = parentId)

    private fun doc(id: String, folderId: String? = null) = Document(
        id = id,
        title = "Doc $id",
        content = null,
        snippet = "",
        folderId = folderId,
        folderName = null,
        isPublic = false,
        updatedAt = null,
    )

    @Test
    fun `build nests folders by parentId under a synthetic root`() {
        val folders = listOf(
            folder("f1", "Work"),
            folder("f2", "Reports", parentId = "f1"),
            folder("f3", "Personal"),
        )

        val root = FolderTree.build(folders, documentsByFolder = emptyMap(), rootDocuments = emptyList())

        assertThat(root.id).isEqualTo(FolderNode.ROOT_ID)
        assertThat(root.isRoot).isTrue()
        assertThat(root.children.map { it.id }).containsExactly("f1", "f3")
        val work = root.children.first { it.id == "f1" }
        assertThat(work.children.map { it.id }).containsExactly("f2")
    }

    @Test
    fun `build attaches embedded documents to their folder and unfiled docs to root`() {
        val folders = listOf(folder("f1", "Work"))
        val root = FolderTree.build(
            folders = folders,
            documentsByFolder = mapOf("f1" to listOf(doc("d1", "f1"))),
            rootDocuments = listOf(doc("rootDoc")),
        )

        assertThat(root.documents.map { it.id }).containsExactly("rootDoc")
        assertThat(root.children.single().documents.map { it.id }).containsExactly("d1")
    }

    @Test
    fun `orphan folders whose parent is unknown are re-parented onto the root`() {
        val folders = listOf(folder("f2", "Reports", parentId = "missing"))

        val root = FolderTree.build(folders, emptyMap(), emptyList())

        assertThat(root.children.map { it.id }).containsExactly("f2")
        assertThat(root.children.single().parentId).isNull()
    }

    @Test
    fun `folders are ordered alphabetically case-insensitively`() {
        val folders = listOf(folder("b", "banana"), folder("a", "Apple"), folder("c", "cherry"))

        val root = FolderTree.build(folders, emptyMap(), emptyList())

        assertThat(root.children.map { it.name }).containsExactly("Apple", "banana", "cherry").inOrder()
    }

    @Test
    fun `contentsOf root returns top-level subfolders and unfiled documents`() {
        val root = FolderTree.build(
            folders = listOf(folder("f1", "Work")),
            documentsByFolder = emptyMap(),
            rootDocuments = listOf(doc("rootDoc")),
        )

        val contents = FolderTree.contentsOf(root, folderId = null)

        assertThat(contents.isRoot).isTrue()
        assertThat(contents.subfolders.map { it.id }).containsExactly("f1")
        assertThat(contents.documents.map { it.id }).containsExactly("rootDoc")
        assertThat(contents.breadcrumb.map { it.name }).containsExactly(FolderNode.ROOT_NAME)
    }

    @Test
    fun `contentsOf a nested folder builds the full breadcrumb path`() {
        val folders = listOf(
            folder("f1", "Work"),
            folder("f2", "Reports", parentId = "f1"),
        )
        val root = FolderTree.build(folders, mapOf("f2" to listOf(doc("d1", "f2"))), emptyList())

        val contents = FolderTree.contentsOf(root, folderId = "f2")

        assertThat(contents.folderId).isEqualTo("f2")
        assertThat(contents.folderName).isEqualTo("Reports")
        assertThat(contents.parentId).isEqualTo("f1")
        assertThat(contents.documents.map { it.id }).containsExactly("d1")
        assertThat(contents.breadcrumb.map { it.name })
            .containsExactly(FolderNode.ROOT_NAME, "Work", "Reports").inOrder()
    }

    @Test
    fun `subfolder summaries carry document and subfolder counts`() {
        val folders = listOf(
            folder("f1", "Work"),
            folder("f2", "Reports", parentId = "f1"),
        )
        val root = FolderTree.build(folders, mapOf("f1" to listOf(doc("d1", "f1"))), emptyList())

        val summary = FolderTree.contentsOf(root, null).subfolders.single()

        assertThat(summary.id).isEqualTo("f1")
        assertThat(summary.documentCount).isEqualTo(1)
        assertThat(summary.subfolderCount).isEqualTo(1)
    }

    @Test
    fun `contentsOf an unknown folder falls back to the root`() {
        val root = FolderTree.build(listOf(folder("f1", "Work")), emptyMap(), emptyList())

        val contents = FolderTree.contentsOf(root, folderId = "does-not-exist")

        assertThat(contents.folderId).isEqualTo(FolderNode.ROOT_ID)
    }
}
