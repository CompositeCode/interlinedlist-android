package com.interlinedlist.android.feature.documents.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * `GET /api/documents/tree` — the combined sidebar tree in one call. Folders nest
 * their own documents (verified live: `folders[].documents`); unfiled documents
 * arrive under `rootDocuments`. This mirrors `/folders` + `/documents` but in a
 * single round-trip, so it can back the browser refresh where that simplifies code.
 */
@Serializable
data class TreeResponse(
    val folders: List<FolderDto> = emptyList(),
    val rootDocuments: List<DocumentDto> = emptyList(),
)
