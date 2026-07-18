package com.interlinedlist.android.feature.messages.data

import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.datastore.SessionStore
import com.interlinedlist.android.feature.messages.data.local.MessageDao
import com.interlinedlist.android.feature.messages.data.local.toDomain
import com.interlinedlist.android.feature.messages.data.local.toEntity
import com.interlinedlist.android.feature.messages.data.remote.MessagesApi
import com.interlinedlist.android.feature.messages.data.remote.dto.CreateMessageRequest
import com.interlinedlist.android.feature.messages.data.remote.dto.PaginationDto
import com.interlinedlist.android.feature.messages.data.remote.dto.toDomain
import com.interlinedlist.android.core.network.error.safeApiCall
import com.interlinedlist.android.feature.messages.domain.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject

class DefaultMessagesRepository @Inject constructor(
    private val api: MessagesApi,
    private val messageDao: MessageDao,
    private val sessionStore: SessionStore,
    private val json: Json,
    private val dispatchers: DispatcherProvider,
) : MessagesRepository {

    override fun observeFeed(): Flow<List<Message>> =
        messageDao.observeFeed().map { rows -> rows.map { it.toDomain() } }

    override fun observeReplies(messageId: String): Flow<List<Message>> =
        messageDao.observeReplies(messageId).map { rows -> rows.map { it.toDomain() } }

    override fun observeMessage(messageId: String): Flow<Message?> =
        messageDao.observeMessage(messageId).map { it?.toDomain() }

    override suspend fun refreshFeed(): ApiResult<Boolean> = withContext(dispatchers.io) {
        when (val result = safeCall { api.getMessages(limit = PaginationDto.DEFAULT_LIMIT, offset = 0) }) {
            is ApiResult.Success -> {
                val page = result.data
                val entities = page.data.mapIndexed { index, dto ->
                    dto.toDomain(currentUserId()).toEntity(feedOrder = index.toLong())
                }
                messageDao.clearFeed()
                messageDao.insertAll(entities)
                ApiResult.Success(page.pagination.hasMore)
            }
            is ApiResult.Failure -> result
        }
    }

    override suspend fun loadMoreFeed(currentCount: Int): ApiResult<Boolean> = withContext(dispatchers.io) {
        when (val result = safeCall {
            api.getMessages(limit = PaginationDto.DEFAULT_LIMIT, offset = currentCount)
        }) {
            is ApiResult.Success -> {
                val page = result.data
                val base = (messageDao.maxFeedOrder() ?: -1L) + 1L
                val entities = page.data.mapIndexed { index, dto ->
                    dto.toDomain(currentUserId()).toEntity(feedOrder = base + index)
                }
                messageDao.insertAll(entities)
                ApiResult.Success(page.pagination.hasMore)
            }
            is ApiResult.Failure -> result
        }
    }

    override suspend fun createMessage(content: String): ApiResult<Message> = withContext(dispatchers.io) {
        when (val result = safeCall { api.createMessage(CreateMessageRequest(content = content)) }) {
            is ApiResult.Success -> {
                val message = result.data.message.toDomain(currentUserId())
                // Insert at the very top of the feed.
                val topOrder = (messageDao.maxFeedOrder() ?: 0L)
                messageDao.upsert(message.toEntity(feedOrder = topOrder - 1L))
                ApiResult.Success(message)
            }
            is ApiResult.Failure -> result
        }
    }

    override suspend fun fetchMessage(messageId: String): ApiResult<Message> = withContext(dispatchers.io) {
        when (val result = safeCall { api.getMessage(messageId) }) {
            is ApiResult.Success -> {
                val message = result.data.message.toDomain(currentUserId())
                messageDao.upsert(message.toEntity(feedOrder = existingOrderOrTop(messageId)))
                ApiResult.Success(message)
            }
            is ApiResult.Failure -> result
        }
    }

    override suspend fun refreshReplies(messageId: String): ApiResult<Unit> = withContext(dispatchers.io) {
        when (val result = safeCall { api.getReplies(messageId) }) {
            is ApiResult.Success -> {
                val entities = result.data.data.mapIndexed { index, dto ->
                    dto.toDomain(currentUserId())
                        .copy(parentId = messageId)
                        .toEntity(feedOrder = index.toLong())
                }
                messageDao.insertAll(entities)
                ApiResult.Success(Unit)
            }
            is ApiResult.Failure -> result
        }
    }

    override suspend fun postReply(parentId: String, content: String): ApiResult<Message> =
        withContext(dispatchers.io) {
            when (val result = safeCall {
                api.createMessage(CreateMessageRequest(content = content, parentId = parentId))
            }) {
                is ApiResult.Success -> {
                    val reply = result.data.message.toDomain(currentUserId()).copy(parentId = parentId)
                    val base = (messageDao.maxFeedOrder() ?: 0L) + 1L
                    messageDao.upsert(reply.toEntity(feedOrder = base))
                    // Reflect the new reply count on the parent if it is cached.
                    bumpReplyCount(parentId, delta = 1)
                    ApiResult.Success(reply)
                }
                is ApiResult.Failure -> result
            }
        }

    override suspend fun setDug(messageId: String, dug: Boolean): ApiResult<Unit> =
        withContext(dispatchers.io) {
            // Optimistically update the cache so the UI reacts immediately.
            val previous = currentEntity(messageId)
            if (previous != null) {
                val delta = if (dug) 1 else -1
                messageDao.upsert(
                    previous.copy(
                        dugByMe = dug,
                        digCount = (previous.digCount + delta).coerceAtLeast(0),
                    ),
                )
            }
            val result = safeCall { if (dug) api.dig(messageId) else api.undig(messageId) }
            if (result is ApiResult.Failure && previous != null) {
                // Roll the optimistic change back on failure.
                messageDao.upsert(previous)
            }
            result
        }

    override suspend fun deleteMessage(messageId: String): ApiResult<Unit> = withContext(dispatchers.io) {
        when (val result = safeCall { api.deleteMessage(messageId) }) {
            is ApiResult.Success -> {
                messageDao.deleteById(messageId)
                ApiResult.Success(Unit)
            }
            is ApiResult.Failure -> result
        }
    }

    override suspend fun search(query: String): ApiResult<List<Message>> = withContext(dispatchers.io) {
        when (val result = safeCall {
            api.search(query = query, limit = PaginationDto.DEFAULT_LIMIT, offset = 0)
        }) {
            is ApiResult.Success ->
                ApiResult.Success(result.data.data.map { it.toDomain(currentUserId()) })
            is ApiResult.Failure -> result
        }
    }

    // --- helpers -----------------------------------------------------------

    private suspend fun <T> safeCall(block: suspend () -> T): ApiResult<T> =
        safeApiCall(json, block)

    private fun currentUserId(): String? = sessionStore.userId

    /** Current cached row for [id], or null. Snapshots the observe Flow. */
    private suspend fun currentEntity(id: String) = messageDao.observeMessage(id).first()

    private suspend fun existingOrderOrTop(id: String): Long =
        currentEntity(id)?.feedOrder ?: ((messageDao.maxFeedOrder() ?: 0L) + 1L)

    private suspend fun bumpReplyCount(parentId: String, delta: Int) {
        val parent = currentEntity(parentId) ?: return
        messageDao.upsert(parent.copy(replyCount = (parent.replyCount + delta).coerceAtLeast(0)))
    }
}
