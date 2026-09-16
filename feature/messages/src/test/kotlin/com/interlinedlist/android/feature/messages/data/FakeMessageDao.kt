package com.interlinedlist.android.feature.messages.data

import com.interlinedlist.android.feature.messages.data.local.FeedEntryEntity
import com.interlinedlist.android.feature.messages.data.local.MAIN_FEED_KEY
import com.interlinedlist.android.feature.messages.data.local.MessageDao
import com.interlinedlist.android.feature.messages.data.local.MessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * In-memory stand-in for the Room [MessageDao] so repository/ViewModel logic can
 * be unit-tested on the JVM without an Android runtime. Mirrors the query
 * semantics of the real DAO: message rows are shared, and a feed is the ordered
 * `feed_entry` membership over them (with the foreign key's cascade on delete).
 */
class FakeMessageDao : MessageDao {

    private val rows = MutableStateFlow<Map<String, MessageEntity>>(emptyMap())

    /** Feed membership, keyed by `feedKey to messageId` like the real primary key. */
    private val entries = MutableStateFlow<Map<Pair<String, String>, FeedEntryEntity>>(emptyMap())

    override fun observeFeed(feedKey: String): Flow<List<MessageEntity>> =
        combine(rows, entries) { messages, membership ->
            membership.values
                .filter { it.feedKey == feedKey }
                .sortedBy { it.position }
                .mapNotNull { messages[it.messageId] }
                .filter { it.parentId == null && it.scheduledAt == null }
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

    override suspend fun upsertFeedEntries(entries: List<FeedEntryEntity>) {
        this.entries.value = this.entries.value.toMutableMap().apply {
            entries.forEach { put(it.feedKey to it.messageId, it) }
        }
    }

    override suspend fun deleteById(id: String) {
        rows.value = rows.value.toMutableMap().apply { remove(id) }
        cascade()
    }

    override suspend fun deleteByAuthorUsername(username: String) {
        rows.value = rows.value.filterValues { it.authorUsername != username }
        cascade()
    }

    override suspend fun clearFeed(feedKey: String) {
        entries.value = entries.value.filterValues { it.feedKey != feedKey }
    }

    override suspend fun clearScheduled() {
        rows.value = rows.value.filterValues { it.scheduledAt == null }
        cascade()
    }

    override suspend fun maxFeedPosition(feedKey: String): Long? =
        entries.value.values.filter { it.feedKey == feedKey }.maxOfOrNull { it.position }

    override suspend fun minFeedPosition(feedKey: String): Long? =
        entries.value.values.filter { it.feedKey == feedKey }.minOfOrNull { it.position }

    override suspend fun maxMessageOrder(): Long? = rows.value.values.maxOfOrNull { it.feedOrder }

    /** Mirrors the `feed_entry -> message` foreign key's ON DELETE CASCADE. */
    private fun cascade() {
        val live = rows.value.keys
        entries.value = entries.value.filterValues { it.messageId in live }
    }

    /** Test helper: current snapshot of a feed, main feed by default. */
    fun feedSnapshot(feedKey: String = MAIN_FEED_KEY): List<MessageEntity> =
        entries.value.values
            .filter { it.feedKey == feedKey }
            .sortedBy { it.position }
            .mapNotNull { rows.value[it.messageId] }
}
