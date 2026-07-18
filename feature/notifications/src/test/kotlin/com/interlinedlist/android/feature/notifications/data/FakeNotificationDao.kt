package com.interlinedlist.android.feature.notifications.data

import com.interlinedlist.android.feature.notifications.data.local.NotificationDao
import com.interlinedlist.android.feature.notifications.data.local.NotificationEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory stand-in for the Room [NotificationDao] so repository/ViewModel logic
 * can be unit-tested on the JVM without an Android runtime. Mirrors the query
 * semantics of the real DAO (ordered by listOrder; unread = read == false).
 */
class FakeNotificationDao : NotificationDao {

    private val rows = MutableStateFlow<Map<String, NotificationEntity>>(emptyMap())

    private fun sorted(): List<NotificationEntity> =
        rows.value.values.sortedBy { it.listOrder }

    override fun observeNotifications(): Flow<List<NotificationEntity>> =
        rows.map { map -> map.values.sortedBy { it.listOrder } }

    override fun observeUnreadCount(): Flow<Int> =
        rows.map { map -> map.values.count { !it.read } }

    override suspend fun insertAll(notifications: List<NotificationEntity>) {
        rows.value = rows.value.toMutableMap().apply {
            notifications.forEach { put(it.id, it) }
        }
    }

    override suspend fun upsert(notification: NotificationEntity) {
        rows.value = rows.value.toMutableMap().apply { put(notification.id, notification) }
    }

    override suspend fun findById(id: String): NotificationEntity? = rows.value[id]

    override suspend fun deleteById(id: String) {
        rows.value = rows.value.toMutableMap().apply { remove(id) }
    }

    override suspend fun markRead(id: String) {
        rows.value[id]?.let { upsertNow(it.copy(read = true)) }
    }

    override suspend fun markAllRead() {
        rows.value = rows.value.mapValues { (_, entity) -> entity.copy(read = true) }
    }

    override suspend fun clear() {
        rows.value = emptyMap()
    }

    override suspend fun maxListOrder(): Long? = sorted().maxOfOrNull { it.listOrder }

    private fun upsertNow(entity: NotificationEntity) {
        rows.value = rows.value.toMutableMap().apply { put(entity.id, entity) }
    }
}
