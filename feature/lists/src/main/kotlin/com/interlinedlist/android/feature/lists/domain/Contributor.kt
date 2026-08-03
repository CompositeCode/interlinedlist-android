package com.interlinedlist.android.feature.lists.domain

/**
 * A person who has contributed rows to a list, ranked by contribution. [addedCount]
 * and [editedCount] break down the work; [score] is their sum (the server's ranking
 * key). This is read-only detail shown alongside a list's watchers.
 */
data class Contributor(
    val userId: String,
    val username: String,
    val displayName: String?,
    val avatarUrl: String?,
    val addedCount: Int,
    val editedCount: Int,
    val score: Int,
) {
    /** Best label for the row: display name when present, else the username. */
    val label: String get() = displayName?.takeIf { it.isNotBlank() } ?: username
}
