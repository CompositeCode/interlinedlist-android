package com.interlinedlist.android.feature.notifications.data

import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.network.error.safeApiCall
import com.interlinedlist.android.feature.notifications.data.local.NotificationDao
import com.interlinedlist.android.feature.notifications.data.local.NotificationEntity
import com.interlinedlist.android.feature.notifications.data.local.toDomain
import com.interlinedlist.android.feature.notifications.data.local.toEntity
import com.interlinedlist.android.feature.notifications.data.remote.NotificationsApi
import com.interlinedlist.android.feature.notifications.data.remote.dto.PaginationDto
import com.interlinedlist.android.feature.notifications.data.remote.dto.toDomain
import com.interlinedlist.android.feature.notifications.domain.Notification
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject

class DefaultNotificationsRepository @Inject constructor(
    private val api: NotificationsApi,
    private val notificationDao: NotificationDao,
    private val json: Json,
    private val dispatchers: DispatcherProvider,
) : NotificationsRepository {

    override fun observeNotifications(): Flow<List<Notification>> =
        notificationDao.observeNotifications().map { rows -> rows.map { it.toDomain() } }

    override fun observeUnreadCount(): Flow<Int> = notificationDao.observeUnreadCount()

    override suspend fun fetchLatest(): ApiResult<List<Notification>> = withContext(dispatchers.io) {
        when (val result = safeCall { api.getNotifications(limit = PaginationDto.DEFAULT_LIMIT, offset = 0) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.items.map { it.toDomain() })
            is ApiResult.Failure -> result
        }
    }

    override suspend fun refresh(): ApiResult<Boolean> = withContext(dispatchers.io) {
        when (val result = safeCall { api.getNotifications(limit = PaginationDto.DEFAULT_LIMIT, offset = 0) }) {
            is ApiResult.Success -> {
                val page = result.data
                val entities = page.items.mapIndexed { index, dto ->
                    dto.toDomain().toEntity(listOrder = index.toLong())
                }
                notificationDao.clear()
                notificationDao.insertAll(entities)
                ApiResult.Success(page.pagination.hasMore)
            }
            is ApiResult.Failure -> result
        }
    }

    override suspend fun loadMore(currentCount: Int): ApiResult<Boolean> = withContext(dispatchers.io) {
        when (val result = safeCall {
            api.getNotifications(limit = PaginationDto.DEFAULT_LIMIT, offset = currentCount)
        }) {
            is ApiResult.Success -> {
                val page = result.data
                val base = (notificationDao.maxListOrder() ?: -1L) + 1L
                val entities = page.items.mapIndexed { index, dto ->
                    dto.toDomain().toEntity(listOrder = base + index)
                }
                notificationDao.insertAll(entities)
                ApiResult.Success(page.pagination.hasMore)
            }
            is ApiResult.Failure -> result
        }
    }

    override suspend fun markRead(id: String): ApiResult<Unit> = withContext(dispatchers.io) {
        val previous = notificationDao.findById(id)
        // Optimistically flip to read so the UI reacts immediately.
        notificationDao.markRead(id)
        val result = safeCall { api.markRead(id) }
        if (result is ApiResult.Failure && previous != null) {
            // Roll back to the pre-mark state on failure.
            notificationDao.upsert(previous)
        }
        result
    }

    override suspend fun markAllRead(): ApiResult<Unit> = withContext(dispatchers.io) {
        // Snapshot current rows so their exact read-states can be restored on failure.
        val snapshot: List<NotificationEntity> = notificationDao.observeNotifications().first()
        notificationDao.markAllRead()
        val result = safeCall { api.markAllRead() }
        if (result is ApiResult.Failure) {
            notificationDao.insertAll(snapshot)
        }
        result
    }

    override suspend fun dismiss(id: String): ApiResult<Unit> = withContext(dispatchers.io) {
        val previous = notificationDao.findById(id)
        // Optimistically remove so the swipe/overflow feels instant.
        notificationDao.deleteById(id)
        val result = safeCall { api.delete(id) }
        if (result is ApiResult.Failure && previous != null) {
            // Restore the row on failure.
            notificationDao.upsert(previous)
        }
        result
    }

    // --- helpers -----------------------------------------------------------

    private suspend fun <T> safeCall(block: suspend () -> T): ApiResult<T> =
        safeApiCall(json, block)
}
