package com.interlinedlist.android.feature.directmessages.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * This module's own Room database, `interlinedlist-dm.db`, kept separate from
 * `:core:database` so Direct Messages own their offline cache end-to-end.
 */
@Database(
    entities = [DirectMessageEntity::class, ConversationEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(DmConverters::class)
abstract class DirectMessagesDatabase : RoomDatabase() {
    abstract fun messageDao(): DirectMessageDao
    abstract fun conversationDao(): ConversationDao
}
