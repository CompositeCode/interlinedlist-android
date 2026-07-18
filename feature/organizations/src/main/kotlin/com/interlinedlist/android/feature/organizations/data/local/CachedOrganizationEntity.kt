package com.interlinedlist.android.feature.organizations.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Locally cached organization — the offline-first source of truth for the index.
 * Members are not cached here; they are loaded per-org on demand on the detail
 * screen.
 */
@Entity(tableName = "cached_organization")
data class CachedOrganizationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String?,
    val avatarUrl: String?,
    val isPublic: Boolean,
    val memberCount: Int,
    val role: String?,
    val updatedAt: String?,
)
