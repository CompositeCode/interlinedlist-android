package com.interlinedlist.android.feature.lists.domain

/**
 * Outcome of a manual refresh of a GitHub-backed list. The counts are optional
 * (the API may only confirm success), so the UI shows a summary when available and
 * a generic confirmation otherwise.
 */
data class RefreshResult(
    val message: String?,
    val added: Int?,
    val updated: Int?,
    val removed: Int?,
) {
    /** A human summary of what changed, or null when the API reported no counts. */
    val summary: String?
        get() {
            val parts = buildList {
                added?.takeIf { it > 0 }?.let { add("$it added") }
                updated?.takeIf { it > 0 }?.let { add("$it updated") }
                removed?.takeIf { it > 0 }?.let { add("$it removed") }
            }
            return parts.takeIf { it.isNotEmpty() }?.joinToString(", ")
        }
}
