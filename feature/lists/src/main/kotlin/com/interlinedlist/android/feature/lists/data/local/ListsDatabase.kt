package com.interlinedlist.android.feature.lists.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room database owned by the Lists feature module. Kept separate from the shared
 * `InterlinedListDatabase` so the feature stays self-contained (the module must
 * not touch `:core:*`).
 */
@Database(
    entities = [CachedListEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class ListsDatabase : RoomDatabase() {
    abstract fun listDao(): ListDao
}
