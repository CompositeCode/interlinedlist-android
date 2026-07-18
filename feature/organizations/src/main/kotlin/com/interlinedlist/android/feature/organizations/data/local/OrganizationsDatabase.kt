package com.interlinedlist.android.feature.organizations.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room database owned by the Organizations feature module. Kept separate from the
 * shared `InterlinedListDatabase` so the feature stays self-contained (the module
 * must not touch `:core:*`).
 */
@Database(
    entities = [CachedOrganizationEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class OrganizationsDatabase : RoomDatabase() {
    abstract fun organizationDao(): OrganizationDao
}
