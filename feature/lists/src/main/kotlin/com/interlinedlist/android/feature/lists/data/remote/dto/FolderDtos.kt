package com.interlinedlist.android.feature.lists.data.remote.dto

import kotlinx.serialization.Serializable

/** A list folder as returned by `GET /api/folders`. */
@Serializable
data class FolderDto(
    val id: String,
    val name: String = "",
    val parentId: String? = null,
)

/** Envelope for `GET /api/folders`; folders may be wrapped or bare. */
@Serializable
data class FoldersResponse(
    val data: List<FolderDto>? = null,
    val folders: List<FolderDto>? = null,
) {
    val items: List<FolderDto> get() = data ?: folders ?: emptyList()
}

/** Body for `POST /api/folders`. */
@Serializable
data class CreateFolderRequest(
    val name: String,
    val parentId: String? = null,
)
