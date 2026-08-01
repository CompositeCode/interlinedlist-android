package com.interlinedlist.android.feature.lists.domain

/**
 * A user's list as shown in the index/feed: enough to render a card and open the
 * detail screen. The full schema and data rows are loaded lazily on the detail
 * screen, so this stays lightweight for the (potentially long) index.
 */
data class ListSummary(
    val id: String,
    val title: String,
    val description: String?,
    val itemCount: Int,
    val folderId: String?,
    val isPublic: Boolean,
    val updatedAt: String?,
)
