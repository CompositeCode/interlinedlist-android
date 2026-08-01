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

/**
 * Body for `PUT /api/folders/{id}` — partial rename/move. Both fields are optional
 * so a rename need not resend the parent (and a move need not resend the name); the
 * shared Json drops nulls so only the supplied fields reach the server.
 */
@Serializable
data class UpdateFolderRequest(
    val name: String? = null,
    val parentId: String? = null,
)

/**
 * Envelope for `PUT /api/folders/{id}`. The updated folder arrives under `folder`
 * (verified against the web handler); `data` is tolerated for forward-compatibility.
 */
@Serializable
data class FolderEnvelope(
    val folder: FolderDto? = null,
    val data: FolderDto? = null,
) {
    val folderOrData: FolderDto? get() = folder ?: data
}
