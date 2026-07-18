package com.interlinedlist.android.feature.documents.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.feature.documents.data.mapper.toDomain
import com.interlinedlist.android.feature.documents.data.mapper.toTemplate
import com.interlinedlist.android.feature.documents.data.remote.dto.DocumentDto
import com.interlinedlist.android.feature.documents.data.remote.dto.FolderDto
import com.interlinedlist.android.feature.documents.domain.Document
import org.junit.Test

class DocumentMappersTest {

    @Test
    fun `document maps fields and derives snippet from content when none supplied`() {
        val dto = DocumentDto(
            id = "d1",
            title = "Notes",
            content = "Line one\nLine two",
            folderId = "f1",
            folderName = "Work",
            isPublic = true,
            updatedAt = "2026-07-18",
        )

        val doc = dto.toDomain()

        assertThat(doc.id).isEqualTo("d1")
        assertThat(doc.title).isEqualTo("Notes")
        assertThat(doc.content).isEqualTo("Line one\nLine two")
        assertThat(doc.snippet).isEqualTo("Line one Line two")
        assertThat(doc.folderId).isEqualTo("f1")
        assertThat(doc.folderName).isEqualTo("Work")
        assertThat(doc.isPublic).isTrue()
        assertThat(doc.updatedAt).isEqualTo("2026-07-18")
    }

    @Test
    fun `document prefers server snippet over derived preview`() {
        val dto = DocumentDto(id = "d1", title = "T", content = "long body", snippet = "server preview")
        assertThat(dto.toDomain().snippet).isEqualTo("server preview")
    }

    @Test
    fun `blank title falls back to Untitled and missing updatedAt uses createdAt`() {
        val dto = DocumentDto(id = "d1", title = "  ", createdAt = "2026-01-01")
        val doc = dto.toDomain()
        assertThat(doc.title).isEqualTo("Untitled")
        assertThat(doc.updatedAt).isEqualTo("2026-01-01")
    }

    @Test
    fun `snippet truncates long content with an ellipsis`() {
        val long = "x".repeat(Document.SNIPPET_MAX + 50)
        val snippet = Document.snippetFrom(long)
        assertThat(snippet.length).isEqualTo(Document.SNIPPET_MAX + 1) // +1 for the ellipsis char
        assertThat(snippet.endsWith("…")).isTrue()
    }

    @Test
    fun `template maps title and snippet`() {
        val dto = DocumentDto(id = "t1", title = "Recipe", content = "Ingredients...")
        val template = dto.toTemplate()
        assertThat(template.id).isEqualTo("t1")
        assertThat(template.title).isEqualTo("Recipe")
        assertThat(template.snippet).isEqualTo("Ingredients...")
    }

    @Test
    fun `folder maps fields with fallback name`() {
        assertThat(FolderDto(id = "f1", name = "Work", parentId = "p1").toDomain().name).isEqualTo("Work")
        assertThat(FolderDto(id = "f1", name = null).toDomain().name).isEqualTo("Untitled folder")
    }

    @Test
    fun `folder maps embedded documents count and timestamps`() {
        val dto = FolderDto(
            id = "f1",
            name = "Work",
            parentId = null,
            documents = listOf(DocumentDto(id = "d1", title = "A")),
            createdAt = "2026-01-01",
            updatedAt = "2026-02-02",
        )
        assertThat(dto.documentsOrEmpty.map { it.id }).containsExactly("d1")
        val folder = dto.toDomain()
        assertThat(folder.createdAt).isEqualTo("2026-01-01")
        assertThat(folder.updatedAt).isEqualTo("2026-02-02")
    }
}
