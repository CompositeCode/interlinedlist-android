package com.interlinedlist.android.feature.messages.data

import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.messages.domain.Message
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first access to the social message feed. Room is the source of truth:
 * reads are Flows off the cache; network refreshes upsert into Room and let the
 * Flows re-emit. Mutations optimistically update the cache where it improves UX.
 */
interface MessagesRepository {

    /** The cached top-level feed, newest-first, re-emitting on every change. */
    fun observeFeed(): Flow<List<Message>>

    /** Cached replies to [messageId], re-emitting on every change. */
    fun observeReplies(messageId: String): Flow<List<Message>>

    /** A single cached message (or null), re-emitting on change. */
    fun observeMessage(messageId: String): Flow<Message?>

    /**
     * Refreshes the first page of the feed from the API and replaces the cached
     * feed. Returns whether more pages are available.
     */
    suspend fun refreshFeed(): ApiResult<Boolean>

    /**
     * Fetches and appends the next feed page after [currentCount] items.
     * Returns whether still more pages remain.
     */
    suspend fun loadMoreFeed(currentCount: Int): ApiResult<Boolean>

    /** Creates a new top-level message and caches it. */
    suspend fun createMessage(content: String): ApiResult<Message>

    /** Fetches a single message and caches it (for the detail screen). */
    suspend fun fetchMessage(messageId: String): ApiResult<Message>

    /** Refreshes the replies of [messageId] from the API into the cache. */
    suspend fun refreshReplies(messageId: String): ApiResult<Unit>

    /** Posts a reply to [parentId] and caches it. */
    suspend fun postReply(parentId: String, content: String): ApiResult<Message>

    /** Digs or undigs a message; optimistically updates the cache. */
    suspend fun setDug(messageId: String, dug: Boolean): ApiResult<Unit>

    /** Deletes one of the caller's own messages, removing it from the cache. */
    suspend fun deleteMessage(messageId: String): ApiResult<Unit>

    /** Full-text search over top-level messages (does not touch the feed cache). */
    suspend fun search(query: String): ApiResult<List<Message>>
}
