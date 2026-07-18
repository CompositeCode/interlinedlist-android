package com.interlinedlist.android.feature.documents.data.mapper

import com.interlinedlist.android.feature.documents.data.remote.dto.DocumentDto
import com.interlinedlist.android.feature.documents.data.remote.dto.FolderDto
import com.interlinedlist.android.feature.documents.domain.Document
import com.interlinedlist.android.feature.documents.domain.DocumentFolder
import com.interlinedlist.android.feature.documents.domain.DocumentTemplate
import com.interlinedlist.android.feature.documents.domain.Pagination
import com.interlinedlist.android.feature.documents.data.remote.dto.PaginationDto

/**
 * Maps a document wire model into the domain [Document]. The snippet prefers a
 * server-provided preview, then any inline body, so index rows still show context
 * even when the list response omits the full [content].
 */
fun DocumentDto.toDomain(): Document {
    val body = content
    val preview = snippet?.takeIf { it.isNotBlank() }
        ?: excerpt?.takeIf { it.isNotBlank() }
        ?: Document.snippetFrom(body)
    return Document(
        id = id,
        title = title?.takeIf { it.isNotBlank() } ?: "Untitled",
        content = body,
        snippet = preview,
        folderId = folderId,
        folderName = folderName,
        isPublic = isPublic,
        updatedAt = updatedAt ?: createdAt,
    )
}

/** Maps a document into a lightweight [DocumentTemplate] for the picker. */
fun DocumentDto.toTemplate(): DocumentTemplate = DocumentTemplate(
    id = id,
    title = title?.takeIf { it.isNotBlank() } ?: "Untitled template",
    snippet = snippet?.takeIf { it.isNotBlank() }
        ?: excerpt?.takeIf { it.isNotBlank() }
        ?: Document.snippetFrom(content),
)

fun FolderDto.toDomain(): DocumentFolder = DocumentFolder(
    id = id,
    name = name?.takeIf { it.isNotBlank() } ?: "Untitled folder",
    parentId = parentId,
)

fun PaginationDto?.toPaginationDomain(fallbackCount: Int): Pagination =
    if (this == null) {
        Pagination.single(fallbackCount)
    } else {
        Pagination(total = total, limit = limit, offset = offset, hasMore = hasMore)
    }
