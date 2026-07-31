package com.interlinedlist.android.feature.directmessages.data

import com.interlinedlist.android.core.common.result.ApiResult
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first access to Direct Messages. Room is the single source of truth:
 * reads are exposed as Flows from the local cache and refresh calls reconcile it
 * against the API.
 */
interface DirectMessagesRepository {

    /** The signed-in user's id, used to tell "mine" from "theirs" in a thread. */
    val currentUserId: String?

    /** Newest-first stream of cached conversation summaries. */
    fun observeConversations(): Flow<List<Conversation>>

    /** Oldest-first stream of a single conversation's cached messages. */
    fun observeThread(username: String): Flow<List<DirectMessage>>

    /**
     * Refreshes one page of the inbox. Pass `null` to load the first page; pass
     * the previous result's cursor to page. Returns the next cursor (or null).
     */
    suspend fun refreshInbox(cursor: String? = null): ApiResult<String?>

    /** Loads and caches the full thread with [username]. */
    suspend fun refreshThread(username: String): ApiResult<Unit>

    /**
     * Polls for messages newer than the newest cached one and merges them.
     * Returns the number of newly merged messages.
     */
    suspend fun pollThreadUpdates(username: String): ApiResult<Int>

    /**
     * Sends a message to [username]. The message is cached optimistically before
     * the network round-trip and reconciled with the server's copy on success.
     */
    suspend fun send(
        username: String,
        body: String,
        imageUrls: List<String> = emptyList(),
    ): ApiResult<DirectMessage>

    /** Marks a received message read locally and on the server. */
    suspend fun markRead(id: String): ApiResult<Unit>

    /** Soft-deletes the caller's side of a message. */
    suspend fun trash(id: String): ApiResult<Unit>

    /** Restores a previously trashed message. */
    suspend fun restore(id: String, username: String): ApiResult<Unit>

    /** The people the current user can DM. */
    suspend fun recipients(): ApiResult<List<Recipient>>

    /** The number of unread received DMs. */
    suspend fun unreadCount(): ApiResult<Int>
}
