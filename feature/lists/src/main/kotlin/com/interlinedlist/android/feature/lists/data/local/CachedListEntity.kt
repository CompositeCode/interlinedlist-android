package com.interlinedlist.android.feature.lists.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Locally cached list summary — the offline-first source of truth for the index.
 * Schema and data rows are not cached here; they are loaded per-list on demand.
 */
@Entity(tableName = "cached_list")
data class CachedListEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String?,
    val itemCount: Int,
    val folderId: String?,
    val isPublic: Boolean,
    val updatedAt: String?,
)
