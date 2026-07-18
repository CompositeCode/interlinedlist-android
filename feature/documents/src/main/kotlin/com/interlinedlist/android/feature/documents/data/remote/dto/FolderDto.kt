package com.interlinedlist.android.feature.documents.data.remote.dto

import kotlinx.serialization.Serializable

/** Wire model for a document folder. */
@Serializable
data class FolderDto(
    val id: String,
    val name: String? = null,
    val parentId: String? = null,
)

/** `GET /api/documents/folders`; folders may arrive under `data` or `folders`. */
@Serializable
data class FolderListResponse(
    val data: List<FolderDto>? = null,
    val folders: List<FolderDto>? = null,
) {
    val foldersOrEmpty: List<FolderDto> get() = data ?: folders ?: emptyList()
}

/** A single folder, returned either bare or wrapped in `{ "folder": ... }`. */
@Serializable
data class FolderResponse(
    val folder: FolderDto? = null,
    val id: String? = null,
    val name: String? = null,
    val parentId: String? = null,
) {
    val folderOrSelf: FolderDto?
        get() = folder ?: id?.let { FolderDto(id = it, name = name, parentId = parentId) }
}

/** Body for `POST /api/documents/folders`. */
@Serializable
data class CreateFolderRequest(
    val name: String,
    val parentId: String? = null,
)
