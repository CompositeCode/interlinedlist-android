package com.interlinedlist.android.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.interlinedlist.android.core.database.dao.UserDao
import com.interlinedlist.android.core.database.entity.CachedUserEntity

/**
 * The app's local cache. Entities and the version grow as each feature phase
 * adds offline support; migrations will be introduced once the schema is no
 * longer disposable.
 */
@Database(
    entities = [CachedUserEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class InterlinedListDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
}
