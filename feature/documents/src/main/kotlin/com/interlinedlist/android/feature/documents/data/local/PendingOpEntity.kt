package com.interlinedlist.android.feature.documents.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A queued local document mutation awaiting push to the server via
 * `POST /api/documents/sync`. Keyed by [documentId] (one pending op per document —
 * a newer edit coalesces onto the same row) so the queue stays small and the last
 * local write wins locally. [op] is `update` or `delete`.
 */
@Entity(tableName = "pending_op")
data class PendingOpEntity(
    @PrimaryKey val documentId: String,
    val op: String,
    val title: String? = null,
    val content: String? = null,
    val isPublic: Boolean? = null,
    val folderId: String? = null,
    val version: Int? = null,
    val queuedAt: Long = 0L,
) {
    companion object {
        const val OP_UPDATE = "update"
        const val OP_DELETE = "delete"
    }
}
