package com.interlinedlist.android.feature.documents.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.interlinedlist.android.feature.documents.domain.Document

/**
 * Locally cached document. This module's Room database is the source of truth for
 * the index; [content] is null for rows only ever seen in a list response and is
 * populated once the detail is fetched. [sortOrder] preserves server ordering so
 * the index renders in the same sequence the API returned.
 */
@Entity(tableName = "document")
data class DocumentEntity(
    @PrimaryKey val id: String,
    val title: String,
    val content: String?,
    val snippet: String,
    val folderId: String?,
    val folderName: String?,
    val isPublic: Boolean,
    val updatedAt: String?,
    val sortOrder: Int,
)

fun DocumentEntity.toDomain(): Document = Document(
    id = id,
    title = title,
    content = content,
    snippet = snippet,
    folderId = folderId,
    folderName = folderName,
    isPublic = isPublic,
    updatedAt = updatedAt,
)

/**
 * Maps a domain document to its cache row. Preserves an existing cached body when
 * the incoming document (e.g. from a list refresh) has none, so we never drop a
 * body we already fetched.
 */
fun Document.toEntity(sortOrder: Int, existingContent: String? = null): DocumentEntity =
    DocumentEntity(
        id = id,
        title = title,
        content = content ?: existingContent,
        snippet = snippet,
        folderId = folderId,
        folderName = folderName,
        isPublic = isPublic,
        updatedAt = updatedAt,
        sortOrder = sortOrder,
    )
