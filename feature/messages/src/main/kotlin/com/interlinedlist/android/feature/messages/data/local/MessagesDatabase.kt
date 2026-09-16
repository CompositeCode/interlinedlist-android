package com.interlinedlist.android.feature.messages.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * This feature module's own Room cache, separate from `:core:database`'s
 * `InterlinedListDatabase`. A disposable cache during early development.
 */
@Database(
    entities = [MessageEntity::class, FeedEntryEntity::class],
    version = 6,
    exportSchema = false,
)
@TypeConverters(MessageConverters::class)
abstract class MessagesDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
}
