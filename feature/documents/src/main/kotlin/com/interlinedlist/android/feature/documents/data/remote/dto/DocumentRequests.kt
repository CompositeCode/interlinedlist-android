package com.interlinedlist.android.feature.documents.data.remote.dto

import kotlinx.serialization.Serializable

/** Body for `POST /api/documents` and `POST /api/documents/folders/{id}/documents`. */
@Serializable
data class CreateDocumentRequest(
    val title: String,
    val content: String,
    val isPublic: Boolean = false,
)

/**
 * Body for `POST /api/documents/folders/{id}/documents` — create a document directly
 * inside a folder. The folder is the path parameter, so the body only carries the
 * document fields (the optional [relativePath] lets the server derive a file name).
 */
@Serializable
data class CreateFolderDocumentRequest(
    val title: String,
    val content: String,
    val isPublic: Boolean = false,
    val relativePath: String? = null,
)

/** Body for `PUT`/`PATCH /api/documents/{id}`. Null fields are left unchanged. */
@Serializable
data class UpdateDocumentRequest(
    val title: String? = null,
    val content: String? = null,
    val isPublic: Boolean? = null,
    val folderId: String? = null,
)

/** Body for `POST /api/documents/from-template`. */
@Serializable
data class FromTemplateRequest(
    val templateDocumentId: String,
    val targetFolderId: String? = null,
)
