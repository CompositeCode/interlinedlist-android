package com.interlinedlist.android.feature.documents.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Wire model for a document. The list endpoints may omit [content]; the detail
 * endpoint includes it. All fields beyond [id] are defaulted so the DTO tolerates
 * the shape variation across list/detail responses (with `ignoreUnknownKeys`).
 */
@Serializable
data class DocumentDto(
    val id: String,
    val title: String? = null,
    val content: String? = null,
    // Some responses expose a server-computed preview; we fall back to content.
    val snippet: String? = null,
    val excerpt: String? = null,
    val folderId: String? = null,
    val folderName: String? = null,
    val isPublic: Boolean = false,
    val updatedAt: String? = null,
    val createdAt: String? = null,
    // Optimistic-concurrency token supplied by the detail/sync endpoints.
    val version: Int? = null,
)
