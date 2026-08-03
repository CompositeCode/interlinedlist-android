package com.interlinedlist.android.feature.documents.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * A folder as returned by the delta-sync endpoint. Unlike [FolderDto] (the nested
 * tree), the sync row is flat and carries a [deletedAt] tombstone so the client can
 * reconcile removals as well as upserts.
 */
@Serializable
data class SyncFolderDto(
    val id: String,
    val name: String? = null,
    val parentId: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val deletedAt: String? = null,
) {
    /** True when this row is a tombstone (soft-deleted server-side). */
    val isDeleted: Boolean get() = deletedAt != null
}

/**
 * A document as returned by the delta-sync endpoint: flat, versioned, and with a
 * [deletedAt] tombstone. [version] is the optimistic-concurrency token used by
 * `PATCH`'s `If-Match` header.
 */
@Serializable
data class SyncDocumentDto(
    val id: String,
    val title: String? = null,
    val content: String? = null,
    val folderId: String? = null,
    val isPublic: Boolean = false,
    val updatedAt: String? = null,
    val createdAt: String? = null,
    val version: Int? = null,
    val deletedAt: String? = null,
) {
    val isDeleted: Boolean get() = deletedAt != null
}

/**
 * `GET /api/documents/sync[?lastSyncAt=<cursor>]` — the delta PULL. Returns folders
 * and documents changed since the cursor (both upserts and [deletedAt] tombstones)
 * plus a fresh [lastSyncAt] to persist as the next cursor. With no cursor the server
 * returns the full set; a future cursor returns empty deltas.
 */
@Serializable
data class SyncPullResponse(
    val folders: List<SyncFolderDto> = emptyList(),
    val documents: List<SyncDocumentDto> = emptyList(),
    val lastSyncAt: String? = null,
)

/**
 * One queued local mutation to replay against the server via `POST /api/documents/sync`.
 * [op] is `update` or `delete`; other fields are the payload for an update.
 */
@Serializable
data class SyncOperationDto(
    val id: String,
    val op: String,
    val title: String? = null,
    val content: String? = null,
    val isPublic: Boolean? = null,
    val folderId: String? = null,
    val version: Int? = null,
)

/**
 * Body for the batch PUSH (`POST /api/documents/sync`). The server models
 * `operations` loosely (a string in the spec); we send a typed JSON array which the
 * shared, lenient Json serialises.
 */
@Serializable
data class SyncPushRequest(
    val operations: List<SyncOperationDto>,
)
