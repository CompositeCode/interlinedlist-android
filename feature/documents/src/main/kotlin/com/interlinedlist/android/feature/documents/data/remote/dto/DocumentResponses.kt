package com.interlinedlist.android.feature.documents.data.remote.dto

import kotlinx.serialization.Serializable

/** Offset/limit paging envelope shared by the list responses. */
@Serializable
data class PaginationDto(
    val total: Int = 0,
    val limit: Int = 20,
    val offset: Int = 0,
    val hasMore: Boolean = false,
)

/**
 * `GET /api/documents` (and folder listings / search). Documents may arrive under
 * `data` (the documented list envelope) or, in some responses, `documents`; both
 * are accepted and merged by [documentsOrEmpty].
 */
@Serializable
data class DocumentListResponse(
    val data: List<DocumentDto>? = null,
    val documents: List<DocumentDto>? = null,
    val pagination: PaginationDto? = null,
) {
    val documentsOrEmpty: List<DocumentDto> get() = data ?: documents ?: emptyList()
}

/**
 * A single document, returned either bare or wrapped in `{ "document": ... }`.
 * [documentOrSelf] resolves whichever form the endpoint used.
 */
@Serializable
data class DocumentResponse(
    val document: DocumentDto? = null,
    val id: String? = null,
    val title: String? = null,
    val content: String? = null,
    val snippet: String? = null,
    val excerpt: String? = null,
    val folderId: String? = null,
    val folderName: String? = null,
    val isPublic: Boolean = false,
    val updatedAt: String? = null,
    val createdAt: String? = null,
) {
    /** The document payload, whether wrapped or inlined at the top level. */
    val documentOrSelf: DocumentDto?
        get() = document ?: id?.let {
            DocumentDto(
                id = it,
                title = title,
                content = content,
                snippet = snippet,
                excerpt = excerpt,
                folderId = folderId,
                folderName = folderName,
                isPublic = isPublic,
                updatedAt = updatedAt,
                createdAt = createdAt,
            )
        }
}
