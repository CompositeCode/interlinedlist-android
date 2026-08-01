package com.interlinedlist.android.feature.profile.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * This feature's own Room cache — kept separate from the shared
 * `InterlinedListDatabase` so the module stays self-contained (see the
 * engineering brief). Disposable during development via destructive migration.
 */
@Database(
    entities = [ProfileEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class ProfileDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
}
