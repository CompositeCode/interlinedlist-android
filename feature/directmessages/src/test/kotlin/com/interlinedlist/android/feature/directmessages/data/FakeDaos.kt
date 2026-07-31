package com.interlinedlist.android.feature.directmessages.data

import com.interlinedlist.android.feature.directmessages.data.local.ConversationDao
import com.interlinedlist.android.feature.directmessages.data.local.ConversationEntity
import com.interlinedlist.android.feature.directmessages.data.local.DirectMessageDao
import com.interlinedlist.android.feature.directmessages.data.local.DirectMessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [DirectMessageDao] so repository tests run on the plain JVM without
 * the Room/Android runtime. Mirrors the real DAO's query semantics.
 */
class FakeMessageDao : DirectMessageDao {
    private val state = MutableStateFlow<List<DirectMessageEntity>>(emptyList())

    val all: List<DirectMessageEntity> get() = state.value

    override fun observeThread(username: String): Flow<List<DirectMessageEntity>> =
        state.map { list ->
            list.filter { it.conversationUsername == username && !it.trashed }
                .sortedBy { it.createdAtMillis }
        }

    override suspend fun upsertAll(messages: List<DirectMessageEntity>) {
        messages.forEach { upsert(it) }
    }

    override suspend fun upsert(message: DirectMessageEntity) {
        state.value = state.value.filterNot { it.id == message.id } + message
    }

    override suspend fun setReadAt(id: String, readAt: String) {
        state.value = state.value.map { if (it.id == id) it.copy(readAt = readAt) else it }
    }

    override suspend fun setTrashed(id: String, trashed: Boolean) {
        state.value = state.value.map { if (it.id == id) it.copy(trashed = trashed) else it }
    }

    override suspend fun deleteById(id: String) {
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun latestCreatedAt(username: String): String? =
        state.value.filter { it.conversationUsername == username }
            .maxByOrNull { it.createdAtMillis }
            ?.createdAt

    override suspend fun existingIds(username: String): List<String> =
        state.value.filter { it.conversationUsername == username }.map { it.id }

    override suspend fun clear() {
        state.value = emptyList()
    }
}

/** In-memory [ConversationDao] counterpart for repository tests. */
class FakeConversationDao : ConversationDao {
    private val state = MutableStateFlow<List<ConversationEntity>>(emptyList())

    override fun observeConversations(): Flow<List<ConversationEntity>> =
        state.map { list -> list.sortedByDescending { it.lastMessageAtMillis } }

    override suspend fun upsertAll(conversations: List<ConversationEntity>) {
        val byKey = state.value.associateBy { it.username }.toMutableMap()
        conversations.forEach { byKey[it.username] = it }
        state.value = byKey.values.toList()
    }

    override suspend fun clearUnread(username: String) {
        state.value = state.value.map {
            if (it.username == username) it.copy(hasUnread = false) else it
        }
    }

    override suspend fun clear() {
        state.value = emptyList()
    }
}
