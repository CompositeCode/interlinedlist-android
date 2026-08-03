package com.interlinedlist.android.feature.directmessages.data

import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.map
import com.interlinedlist.android.core.network.error.safeApiCall
import com.interlinedlist.android.feature.directmessages.data.local.ConversationDao
import com.interlinedlist.android.feature.directmessages.data.local.ConversationEntity
import com.interlinedlist.android.feature.directmessages.data.local.DirectMessageDao
import com.interlinedlist.android.feature.directmessages.data.local.DirectMessageEntity
import com.interlinedlist.android.feature.directmessages.data.remote.DirectMessagesApi
import com.interlinedlist.android.feature.directmessages.data.remote.dto.MessageDto
import com.interlinedlist.android.feature.directmessages.data.remote.dto.RecipientDto
import com.interlinedlist.android.feature.directmessages.data.remote.dto.SendMessageRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.Instant
import javax.inject.Inject

/**
 * Offline-first Direct Messages repository. Room is the single source of truth:
 * `observe*` return Flows straight from the DAOs, and every network call folds
 * its result back into the cache so the UI updates reactively.
 */
class DefaultDirectMessagesRepository @Inject constructor(
    private val api: DirectMessagesApi,
    private val messageDao: DirectMessageDao,
    private val conversationDao: ConversationDao,
    private val currentUserIdProvider: CurrentUserIdProvider,
    private val json: Json,
    private val dispatchers: DispatcherProvider,
) : DirectMessagesRepository {

    override val currentUserId: String? get() = currentUserIdProvider.currentUserId()

    override fun observeConversations(): Flow<List<Conversation>> =
        conversationDao.observeConversations().map { list -> list.map { it.toDomain() } }

    override fun observeThread(username: String): Flow<List<DirectMessage>> =
        messageDao.observeThread(username).map { list -> list.map { it.toDomain() } }

    override suspend fun refreshInbox(cursor: String?): ApiResult<String?> =
        withContext(dispatchers.io) {
            safeApiCall(json) { api.getInbox(cursor = cursor) }.let { result ->
                when (result) {
                    is ApiResult.Success -> {
                        val summaries = result.data.items.mapNotNull { it.toConversationSummary() }
                        if (summaries.isNotEmpty()) conversationDao.upsertAll(summaries)
                        ApiResult.Success(result.data.nextCursor)
                    }
                    is ApiResult.Failure -> result
                }
            }
        }

    override suspend fun refreshThread(username: String): ApiResult<Unit> =
        withContext(dispatchers.io) {
            safeApiCall(json) { api.getThread(username) }.let { result ->
                when (result) {
                    is ApiResult.Success -> {
                        val entities = result.data.items.map { it.toEntity(username) }
                        if (entities.isNotEmpty()) messageDao.upsertAll(entities)
                        conversationDao.clearUnread(username)
                        ApiResult.Success(Unit)
                    }
                    is ApiResult.Failure -> result
                }
            }
        }

    override suspend fun pollThreadUpdates(username: String): ApiResult<Int> =
        withContext(dispatchers.io) {
            val after = messageDao.latestCreatedAt(username)
            safeApiCall(json) { api.getThreadUpdates(username, after = after) }.let { result ->
                when (result) {
                    is ApiResult.Success -> {
                        val known = messageDao.existingIds(username).toSet()
                        val fresh = result.data.items
                            .map { it.toEntity(username) }
                            .filter { it.id !in known }
                        if (fresh.isNotEmpty()) messageDao.upsertAll(fresh)
                        ApiResult.Success(fresh.size)
                    }
                    is ApiResult.Failure -> result
                }
            }
        }

    override suspend fun send(
        username: String,
        body: String,
        imageUrls: List<String>,
    ): ApiResult<DirectMessage> = withContext(dispatchers.io) {
        // 1) Optimistic local echo so the message appears instantly.
        val nowIso = Instant.now().toString()
        val optimisticId = "local-${System.nanoTime()}"
        val optimistic = DirectMessageEntity(
            id = optimisticId,
            conversationUsername = username,
            senderId = currentUserId.orEmpty(),
            recipientId = "",
            body = body,
            imageUrls = imageUrls,
            createdAt = nowIso,
            createdAtMillis = parseIsoMillis(nowIso),
            readAt = null,
            trashed = false,
            pending = true,
        )
        messageDao.upsert(optimistic)

        // 2) Send, then swap the optimistic copy for the server's authoritative one.
        val request = SendMessageRequest(
            recipientUsername = username,
            body = body,
            imageUrls = imageUrls,
        )
        when (val result = safeApiCall(json) { api.send(request) }) {
            is ApiResult.Success -> {
                val serverMessage = result.data.message()
                if (serverMessage != null && serverMessage.id.isNotBlank()) {
                    messageDao.deleteById(optimisticId)
                    val entity = serverMessage.toEntity(username)
                    messageDao.upsert(entity)
                    ApiResult.Success(entity.toDomain())
                } else {
                    // Server accepted but returned no body; keep the optimistic copy.
                    val confirmed = optimistic.copy(pending = false)
                    messageDao.upsert(confirmed)
                    ApiResult.Success(confirmed.toDomain())
                }
            }
            is ApiResult.Failure -> result // optimistic copy remains for retry
        }
    }

    override suspend fun markRead(id: String): ApiResult<Unit> = withContext(dispatchers.io) {
        safeApiCall(json) { api.markRead(id) }.let { result ->
            when (result) {
                is ApiResult.Success -> {
                    messageDao.setReadAt(id, Instant.now().toString())
                    ApiResult.Success(Unit)
                }
                is ApiResult.Failure -> result
            }
        }
    }

    override suspend fun trash(id: String): ApiResult<Unit> = withContext(dispatchers.io) {
        safeApiCall(json) { api.trash(id) }.let { result ->
            when (result) {
                is ApiResult.Success -> {
                    messageDao.setTrashed(id, true)
                    ApiResult.Success(Unit)
                }
                is ApiResult.Failure -> result
            }
        }
    }

    override suspend fun restore(id: String, username: String): ApiResult<Unit> =
        withContext(dispatchers.io) {
            safeApiCall(json) { api.restore(id) }.let { result ->
                when (result) {
                    is ApiResult.Success -> {
                        messageDao.setTrashed(id, false)
                        ApiResult.Success(Unit)
                    }
                    is ApiResult.Failure -> result
                }
            }
        }

    override suspend fun recipients(): ApiResult<List<Recipient>> = withContext(dispatchers.io) {
        safeApiCall(json) { api.getRecipients() }
            .map { response -> response.recipients.map(RecipientDto::toDomain) }
    }

    override suspend fun unreadCount(): ApiResult<Int> = withContext(dispatchers.io) {
        safeApiCall(json) { api.getUnreadCount() }.map { it.count }
    }

    /**
     * Folds an inbox message into a conversation summary. The other participant
     * is whichever end of the message is not the current user; the embedded
     * author sub-object (present on the inbox endpoint) supplies display info.
     */
    private fun MessageDto.toConversationSummary(): ConversationEntity? {
        val me = currentUserId
        val other = embeddedAuthor
        val otherUsername = other?.username
            ?: return null // Without a username we cannot key the conversation.
        val received = me != null && recipientId == me
        return ConversationEntity(
            username = otherUsername,
            displayName = other.displayName,
            avatarUrl = other.avatar,
            lastMessageId = id,
            lastMessageBody = body,
            lastMessageAtMillis = parseIsoMillis(createdAt),
            hasUnread = received && readAt == null,
        )
    }
}
