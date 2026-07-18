package com.interlinedlist.android.feature.notifications.data

import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.notifications.domain.Notification
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first access to the recipient's notifications. Room is the source of
 * truth: reads are Flows off the cache; network refreshes upsert into Room and let
 * the Flows re-emit. Mutations (read / mark-all-read / dismiss) optimistically
 * update the cache and roll back on failure.
 *
 * Notifications are polled (no FCM yet) — the ViewModel refreshes on load and on
 * pull-to-refresh.
 */
interface NotificationsRepository {

    /** The cached notifications, newest-first, re-emitting on every change. */
    fun observeNotifications(): Flow<List<Notification>>

    /** Live count of unread notifications (drives the badge/header styling). */
    fun observeUnreadCount(): Flow<Int>

    /**
     * Refreshes the first page from the API and replaces the cached list.
     * Returns whether more pages are available.
     */
    suspend fun refresh(): ApiResult<Boolean>

    /**
     * Fetches and appends the next page after [currentCount] items.
     * Returns whether still more pages remain.
     */
    suspend fun loadMore(currentCount: Int): ApiResult<Boolean>

    /** Marks a single notification read; optimistically updates the cache. */
    suspend fun markRead(id: String): ApiResult<Unit>

    /** Marks every notification read; optimistically updates the cache. */
    suspend fun markAllRead(): ApiResult<Unit>

    /** Dismisses (deletes) a notification, removing it from the cache. */
    suspend fun dismiss(id: String): ApiResult<Unit>
}
