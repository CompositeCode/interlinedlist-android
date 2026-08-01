package com.interlinedlist.android.feature.documents.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Wire model for a document folder. The live `GET /api/documents/folders` response
 * nests folders via [parentId] (null == root) and embeds each folder's own
 * [documents], so the entire tree arrives in a single call.
 */
@Serializable
data class FolderDto(
    val id: String,
    val name: String? = null,
    val parentId: String? = null,
    val documents: List<DocumentDto>? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
) {
    val documentsOrEmpty: List<DocumentDto> get() = documents ?: emptyList()
}

/**
 * `GET /api/documents/folders`. The live API returns folders under `folders`; a
 * `data` envelope is also tolerated for forward-compatibility.
 */
@Serializable
data class FolderListResponse(
    val data: List<FolderDto>? = null,
    val folders: List<FolderDto>? = null,
) {
    val foldersOrEmpty: List<FolderDto> get() = folders ?: data ?: emptyList()
}

/** A single folder, returned either bare or wrapped in `{ "folder": ... }`. */
@Serializable
data class FolderResponse(
    val folder: FolderDto? = null,
    val id: String? = null,
    val name: String? = null,
    val parentId: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
) {
    val folderOrSelf: FolderDto?
        get() = folder ?: id?.let {
            FolderDto(
                id = it,
                name = name,
                parentId = parentId,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
        }
}

/** Body for `POST /api/documents/folders`. */
@Serializable
data class CreateFolderRequest(
    val name: String,
    val parentId: String? = null,
)

/**
 * Body for `PUT /api/documents/folders/{id}` — rename and/or move (re-parent).
 * Null fields are left unchanged by the server.
 */
@Serializable
data class UpdateFolderRequest(
    val name: String? = null,
    val parentId: String? = null,
)
