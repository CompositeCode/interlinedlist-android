package com.interlinedlist.android.feature.notifications.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {

    /** Cached notifications in server order; re-emits on every change. */
    @Query("SELECT * FROM notification ORDER BY listOrder ASC")
    fun observeNotifications(): Flow<List<NotificationEntity>>

    /** Live count of unread notifications, for the badge/header. */
    @Query("SELECT COUNT(*) FROM notification WHERE read = 0")
    fun observeUnreadCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notifications: List<NotificationEntity>)

    @Upsert
    suspend fun upsert(notification: NotificationEntity)

    /** A single cached row (or null); used for optimistic mutations. */
    @Query("SELECT * FROM notification WHERE id = :id")
    suspend fun findById(id: String): NotificationEntity?

    @Query("DELETE FROM notification WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Marks one row read in place (optimistic mark-as-read). */
    @Query("UPDATE notification SET read = 1 WHERE id = :id")
    suspend fun markRead(id: String)

    /** Marks every cached row read (optimistic mark-all-read). */
    @Query("UPDATE notification SET read = 1")
    suspend fun markAllRead()

    /** Clears the cache (used before writing a fresh refresh page). */
    @Query("DELETE FROM notification")
    suspend fun clear()

    /** Largest list-order position currently stored (for append/load-more). */
    @Query("SELECT MAX(listOrder) FROM notification")
    suspend fun maxListOrder(): Long?
}
