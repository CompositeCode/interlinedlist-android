package com.interlinedlist.android.feature.messages.data

import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.messages.domain.CreatedMessage
import com.interlinedlist.android.feature.messages.domain.CrossPostSelection
import com.interlinedlist.android.feature.messages.domain.LinkedNetwork
import com.interlinedlist.android.feature.messages.domain.Message
import com.interlinedlist.android.feature.messages.domain.ReportReason
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

    /** Cached scheduled (not-yet-published) messages, soonest-first. */
    fun observeScheduled(): Flow<List<Message>>

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

    /**
     * Creates a new top-level message and caches it. Optionally attaches already
     * uploaded [imageUrls] / [videoUrls], defers publishing to [scheduledAt]
     * (ISO-8601), and cross-posts to the already-linked networks named by
     * [crossPost] (InterlinedList-only when [CrossPostSelection.NONE]). A scheduled
     * message does not enter the feed cache. Returns the created message plus any
     * per-network cross-post delivery statuses the endpoint reported.
     */
    suspend fun createMessage(
        content: String,
        imageUrls: List<String> = emptyList(),
        videoUrls: List<String> = emptyList(),
        scheduledAt: String? = null,
        crossPost: CrossPostSelection = CrossPostSelection.NONE,
    ): ApiResult<CreatedMessage>

    /**
     * The caller's already-linked social networks, offered as cross-post
     * destinations in the composer. Read directly from the API (not cached); an
     * empty list means nothing is linked yet.
     */
    suspend fun getLinkedNetworks(): ApiResult<List<LinkedNetwork>>

    /** Uploads image [bytes] and returns the hosted URL to attach on compose. */
    suspend fun uploadImage(bytes: ByteArray, fileName: String, mimeType: String): ApiResult<String>

    /** Uploads video [bytes] and returns the hosted URL to attach on compose. */
    suspend fun uploadVideo(bytes: ByteArray, fileName: String, mimeType: String): ApiResult<String>

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

    /**
     * Edits the [content] of one of the caller's own messages. Optimistically
     * updates the cached message (content + an "edited" marker) and rolls the
     * change back on failure. Returns the updated [Message].
     */
    suspend fun editMessage(messageId: String, content: String): ApiResult<Message>

    /** Refreshes the caller's scheduled messages from the API into the cache. */
    suspend fun refreshScheduled(): ApiResult<Unit>

    /** Cancels a scheduled message (deletes it), removing it from the cache. */
    suspend fun cancelScheduled(messageId: String): ApiResult<Unit>

    /** Reports a message with a [reason] and optional free-text [detail]. */
    suspend fun report(messageId: String, reason: ReportReason, detail: String? = null): ApiResult<Unit>

    /**
     * Blocks the user [username]. On success, removes that author's messages from
     * the local feed/reply cache so the caller stops seeing them immediately.
     */
    suspend fun blockUser(username: String): ApiResult<Unit>

    /**
     * Mutes the user [username]. On success, removes that author's messages from
     * the local feed/reply cache so the caller stops seeing them immediately.
     */
    suspend fun muteUser(username: String): ApiResult<Unit>

    /** Reports the user [username] with a [reason] and optional free-text [detail]. */
    suspend fun reportUser(username: String, reason: ReportReason, detail: String? = null): ApiResult<Unit>

    /**
     * Fetches link-preview metadata for [messageId]'s links and updates the cached
     * message so the feed/detail can render a preview card.
     */
    suspend fun fetchMetadata(messageId: String): ApiResult<Message>

    /** Full-text search over top-level messages (does not touch the feed cache). */
    suspend fun search(query: String): ApiResult<List<Message>>
}
