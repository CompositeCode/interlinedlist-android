package com.interlinedlist.android.feature.notifications.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * This feature module's own Room cache, separate from `:core:database`'s
 * `InterlinedListDatabase`. A disposable cache during early development.
 */
@Database(
    entities = [NotificationEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class NotificationsDatabase : RoomDatabase() {
    abstract fun notificationDao(): NotificationDao
}
