package com.interlinedlist.android.feature.messages.data

import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.datastore.SessionStore
import com.interlinedlist.android.feature.messages.data.local.MessageDao
import com.interlinedlist.android.feature.messages.data.local.toDomain
import com.interlinedlist.android.feature.messages.data.local.toEntity
import com.interlinedlist.android.feature.messages.data.remote.MessagesApi
import com.interlinedlist.android.feature.messages.data.remote.dto.CreateMessageRequest
import com.interlinedlist.android.feature.messages.data.remote.dto.EditMessageRequest
import com.interlinedlist.android.feature.messages.data.remote.dto.PaginationDto
import com.interlinedlist.android.feature.messages.data.remote.dto.ReportRequest
import com.interlinedlist.android.feature.messages.data.remote.dto.UserReportRequest
import com.interlinedlist.android.feature.messages.data.remote.dto.toDomain
import com.interlinedlist.android.core.network.error.safeApiCall
import com.interlinedlist.android.feature.messages.domain.CreatedMessage
import com.interlinedlist.android.feature.messages.domain.CrossPostSelection
import com.interlinedlist.android.feature.messages.domain.LinkedNetwork
import com.interlinedlist.android.feature.messages.domain.Message
import com.interlinedlist.android.feature.messages.domain.ReportReason
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
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

    override fun observeScheduled(): Flow<List<Message>> =
        messageDao.observeScheduled().map { rows -> rows.map { it.toDomain() } }

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

    override suspend fun createMessage(
        content: String,
        imageUrls: List<String>,
        videoUrls: List<String>,
        scheduledAt: String?,
        crossPost: CrossPostSelection,
    ): ApiResult<CreatedMessage> = withContext(dispatchers.io) {
        val request = CreateMessageRequest(
            content = content,
            imageUrls = imageUrls.ifEmpty { null },
            videoUrls = videoUrls.ifEmpty { null },
            scheduledAt = scheduledAt,
            // Encode cross-post targets per the create schema. explicitNulls=false
            // drops these when empty/false, so a plain post keeps its original body.
            mastodonProviderIds = crossPost.mastodonProviderIds.ifEmpty { null },
            crossPostToBluesky = crossPost.bluesky.takeIf { it },
            crossPostToLinkedIn = crossPost.linkedIn.takeIf { it },
            crossPostToTwitter = crossPost.twitter.takeIf { it },
        )
        when (val result = safeCall { api.createMessage(request) }) {
            is ApiResult.Success -> {
                val message = result.data.data.toDomain(currentUserId())
                if (message.scheduledAt != null) {
                    // Scheduled messages are cached in the scheduled view, not the feed.
                    messageDao.upsert(message.toEntity(feedOrder = 0L))
                } else {
                    // Insert at the very top of the feed.
                    val topOrder = (messageDao.maxFeedOrder() ?: 0L)
                    messageDao.upsert(message.toEntity(feedOrder = topOrder - 1L))
                }
                val crossPosts = result.data.crossPosts.mapNotNull { it.toDomainOrNull() }
                ApiResult.Success(CreatedMessage(message = message, crossPosts = crossPosts))
            }
            is ApiResult.Failure -> result
        }
    }

    override suspend fun getLinkedNetworks(): ApiResult<List<LinkedNetwork>> = withContext(dispatchers.io) {
        when (val result = safeCall { api.getIdentities() }) {
            is ApiResult.Success -> ApiResult.Success(result.data.identities.map { it.toDomain() })
            is ApiResult.Failure -> result
        }
    }

    override suspend fun uploadImage(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
    ): ApiResult<String> = withContext(dispatchers.io) {
        upload(bytes, fileName, mimeType) { api.uploadImage(it) }
    }

    override suspend fun uploadVideo(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
    ): ApiResult<String> = withContext(dispatchers.io) {
        upload(bytes, fileName, mimeType) { api.uploadVideo(it) }
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
                    val reply = result.data.data.toDomain(currentUserId()).copy(parentId = parentId)
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

    override suspend fun editMessage(messageId: String, content: String): ApiResult<Message> =
        withContext(dispatchers.io) {
            // Optimistically apply the new content + an "edited" marker so the feed
            // and detail react immediately; roll back the whole row on failure.
            val previous = currentEntity(messageId)
            val editedAt = nowIso()
            if (previous != null) {
                messageDao.upsert(previous.copy(content = content, editedAt = editedAt))
            }
            when (val result = safeCall { api.editMessage(messageId, EditMessageRequest(content = content)) }) {
                is ApiResult.Success -> {
                    val updated = (previous?.copy(content = content, editedAt = editedAt))?.toDomain()
                        ?: Message(
                            id = messageId, content = content, authorId = "", authorUsername = "",
                            authorDisplayName = null, authorAvatarUrl = null, createdAt = null,
                            digCount = 0, replyCount = 0, dugByMe = false, parentId = null,
                            mine = true, editedAt = editedAt,
                        )
                    ApiResult.Success(updated)
                }
                is ApiResult.Failure -> {
                    if (previous != null) messageDao.upsert(previous)
                    result
                }
            }
        }

    override suspend fun refreshScheduled(): ApiResult<Unit> = withContext(dispatchers.io) {
        when (val result = safeCall { api.getScheduled() }) {
            is ApiResult.Success -> {
                val entities = result.data.data.mapIndexed { index, dto ->
                    dto.toDomain(currentUserId()).toEntity(feedOrder = index.toLong())
                }
                messageDao.clearScheduled()
                messageDao.insertAll(entities)
                ApiResult.Success(Unit)
            }
            is ApiResult.Failure -> result
        }
    }

    override suspend fun cancelScheduled(messageId: String): ApiResult<Unit> = withContext(dispatchers.io) {
        when (val result = safeCall { api.deleteMessage(messageId) }) {
            is ApiResult.Success -> {
                messageDao.deleteById(messageId)
                ApiResult.Success(Unit)
            }
            is ApiResult.Failure -> result
        }
    }

    override suspend fun report(
        messageId: String,
        reason: ReportReason,
        detail: String?,
    ): ApiResult<Unit> = withContext(dispatchers.io) {
        safeCall {
            api.report(
                id = messageId,
                body = ReportRequest(reason = reason.wireValue, detail = detail?.takeIf { it.isNotBlank() }),
            )
        }
    }

    override suspend fun blockUser(username: String): ApiResult<Unit> = withContext(dispatchers.io) {
        when (val result = safeCall { api.blockUser(username) }) {
            is ApiResult.Success -> {
                // Hide the blocked author's messages from the local cache.
                messageDao.deleteByAuthorUsername(username)
                ApiResult.Success(Unit)
            }
            is ApiResult.Failure -> result
        }
    }

    override suspend fun muteUser(username: String): ApiResult<Unit> = withContext(dispatchers.io) {
        when (val result = safeCall { api.muteUser(username) }) {
            is ApiResult.Success -> {
                // Hide the muted author's messages from the local cache.
                messageDao.deleteByAuthorUsername(username)
                ApiResult.Success(Unit)
            }
            is ApiResult.Failure -> result
        }
    }

    override suspend fun reportUser(
        username: String,
        reason: ReportReason,
        detail: String?,
    ): ApiResult<Unit> = withContext(dispatchers.io) {
        safeCall {
            api.reportUser(
                username = username,
                body = UserReportRequest(
                    reason = reason.wireValue,
                    detail = detail?.takeIf { it.isNotBlank() },
                ),
            )
        }
    }

    override suspend fun fetchMetadata(messageId: String): ApiResult<Message> = withContext(dispatchers.io) {
        when (val result = safeCall { api.fetchMetadata(messageId) }) {
            is ApiResult.Success -> {
                val body = result.data
                val preview = (body.message?.linkMetadata ?: body.linkMetadata)?.toDomain()
                val existing = currentEntity(messageId)
                val updated = when {
                    // Prefer the fully-formed message the endpoint may echo back.
                    body.message != null -> body.message.toDomain(currentUserId())
                        .let { fresh ->
                            existing?.toDomain()?.copy(
                                linkPreview = fresh.linkPreview ?: preview,
                            ) ?: fresh
                        }
                    existing != null -> existing.toDomain().copy(linkPreview = preview)
                    else -> null
                }
                if (updated != null) {
                    messageDao.upsert(updated.toEntity(feedOrder = existingOrderOrTop(messageId)))
                    ApiResult.Success(updated)
                } else {
                    ApiResult.Success(
                        Message(
                            id = messageId, content = "", authorId = "", authorUsername = "",
                            authorDisplayName = null, authorAvatarUrl = null, createdAt = null,
                            digCount = 0, replyCount = 0, dugByMe = false, parentId = null,
                            mine = false, linkPreview = preview,
                        ),
                    )
                }
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

    /** Current instant as an ISO-8601 string, for the optimistic "edited" marker. */
    private fun nowIso(): String = java.time.Instant.now().toString()

    /** Shared multipart upload path; extracts the hosted URL from the response. */
    private suspend fun upload(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
        call: suspend (MultipartBody.Part) -> com.interlinedlist.android.feature.messages.data.remote.dto.MediaUploadResponse,
    ): ApiResult<String> {
        val part = MultipartBody.Part.createFormData(
            name = "file",
            filename = fileName,
            body = bytes.toRequestBody(mimeType.toMediaTypeOrNull()),
        )
        return when (val result = safeCall { call(part) }) {
            is ApiResult.Success -> {
                val url = result.data.hostedUrl
                if (url.isNullOrBlank()) {
                    ApiResult.Failure(
                        com.interlinedlist.android.core.common.result.AppError.Server(
                            "Upload succeeded but no media URL was returned.",
                        ),
                    )
                } else {
                    ApiResult.Success(url)
                }
            }
            is ApiResult.Failure -> result
        }
    }

    /** Current cached row for [id], or null. Snapshots the observe Flow. */
    private suspend fun currentEntity(id: String) = messageDao.observeMessage(id).first()

    private suspend fun existingOrderOrTop(id: String): Long =
        currentEntity(id)?.feedOrder ?: ((messageDao.maxFeedOrder() ?: 0L) + 1L)

    private suspend fun bumpReplyCount(parentId: String, delta: Int) {
        val parent = currentEntity(parentId) ?: return
        messageDao.upsert(parent.copy(replyCount = (parent.replyCount + delta).coerceAtLeast(0)))
    }
}
