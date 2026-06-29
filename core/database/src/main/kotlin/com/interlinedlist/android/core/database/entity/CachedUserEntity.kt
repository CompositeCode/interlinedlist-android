package com.interlinedlist.android.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Locally cached copy of the signed-in user for offline-first reads. */
@Entity(tableName = "cached_user")
data class CachedUserEntity(
    @PrimaryKey val id: String,
    val username: String,
    val displayName: String?,
    val avatarUrl: String?,
    val bio: String?,
    val customerStatus: String?,
)
