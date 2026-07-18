package com.interlinedlist.android.feature.documents.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * This feature's own Room cache — kept separate from the shared
 * `InterlinedListDatabase` so the module stays self-contained (see the
 * engineering brief). Disposable during development via destructive migration.
 */
@Database(
    entities = [DocumentEntity::class, FolderEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class DocumentsDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun folderDao(): FolderDao
}
