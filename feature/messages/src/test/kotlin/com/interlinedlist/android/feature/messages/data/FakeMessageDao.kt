package com.interlinedlist.android.feature.messages.data

import com.interlinedlist.android.feature.messages.data.local.MessageDao
import com.interlinedlist.android.feature.messages.data.local.MessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory stand-in for the Room [MessageDao] so repository/ViewModel logic can
 * be unit-tested on the JVM without an Android runtime. Mirrors the query
 * semantics of the real DAO (feed = parentId null, ordered by feedOrder).
 */
class FakeMessageDao : MessageDao {

    private val rows = MutableStateFlow<Map<String, MessageEntity>>(emptyMap())

    private fun sorted(predicate: (MessageEntity) -> Boolean): List<MessageEntity> =
        rows.value.values.filter(predicate).sortedBy { it.feedOrder }

    override fun observeFeed(): Flow<List<MessageEntity>> =
        rows.map { map ->
            map.values.filter { it.parentId == null && it.scheduledAt == null }.sortedBy { it.feedOrder }
        }

    override fun observeReplies(parentId: String): Flow<List<MessageEntity>> =
        rows.map { map -> map.values.filter { it.parentId == parentId }.sortedBy { it.feedOrder } }

    override fun observeMessage(id: String): Flow<MessageEntity?> =
        rows.map { it[id] }

    override fun observeScheduled(): Flow<List<MessageEntity>> =
        rows.map { map -> map.values.filter { it.scheduledAt != null }.sortedBy { it.scheduledAt } }

    override suspend fun insertAll(messages: List<MessageEntity>) {
        rows.value = rows.value.toMutableMap().apply {
            messages.forEach { put(it.id, it) }
        }
    }

    override suspend fun upsert(message: MessageEntity) {
        rows.value = rows.value.toMutableMap().apply { put(message.id, message) }
    }

    override suspend fun deleteById(id: String) {
        rows.value = rows.value.toMutableMap().apply { remove(id) }
    }

    override suspend fun clearFeed() {
        rows.value = rows.value.filterValues { it.parentId != null || it.scheduledAt != null }
    }

    override suspend fun clearScheduled() {
        rows.value = rows.value.filterValues { it.scheduledAt == null }
    }

    override suspend fun maxFeedOrder(): Long? =
        sorted { it.parentId == null && it.scheduledAt == null }.maxOfOrNull { it.feedOrder }

    /** Test helper: current feed snapshot. */
    fun feedSnapshot(): List<MessageEntity> = sorted { it.parentId == null && it.scheduledAt == null }
}
