package com.interlinedlist.android.feature.documents.domain

/**
 * A markdown document owned by the current user. [snippet] is a short preview of
 * [content] shown in the index; when a document is loaded from a list endpoint
 * (which omits the body) [content] is null until the detail is fetched.
 */
data class Document(
    val id: String,
    val title: String,
    val content: String?,
    val snippet: String,
    val folderId: String?,
    val folderName: String?,
    val isPublic: Boolean,
    val updatedAt: String?,
    /**
     * Optimistic-concurrency token: the server's row version. Sent back as an
     * `If-Match` header on `PATCH` so a save that raced another writer is rejected
     * rather than silently clobbering their change. Null until a versioned response
     * (sync / detail) has populated it.
     */
    val version: Int? = null,
) {
    companion object {
        /** Longest preview we keep for the index snippet, in characters. */
        const val SNIPPET_MAX = 140

        /** Derives a plain-text-ish snippet from a markdown body. */
        fun snippetFrom(content: String?): String {
            if (content.isNullOrBlank()) return ""
            val collapsed = content.replace(Regex("\\s+"), " ").trim()
            return if (collapsed.length <= SNIPPET_MAX) collapsed
            else collapsed.take(SNIPPET_MAX).trimEnd() + "…"
        }
    }
}
