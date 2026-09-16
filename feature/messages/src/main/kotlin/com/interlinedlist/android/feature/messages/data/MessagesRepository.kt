package com.interlinedlist.android.feature.messages.data

import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.model.ViewingPreference
import com.interlinedlist.android.feature.messages.domain.CreatedMessage
import com.interlinedlist.android.feature.messages.domain.CrossPostSelection
import com.interlinedlist.android.feature.messages.domain.LinkedNetwork
import com.interlinedlist.android.feature.messages.domain.Message
import com.interlinedlist.android.feature.messages.domain.MessageVisibility
import com.interlinedlist.android.feature.messages.domain.ReportReason
import com.interlinedlist.android.feature.messages.domain.TagSuggestion
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
     * feed, restarting keyset pagination from the top. Returns the opaque cursor
     * for the next page, or null when the feed ends here.
     *
     * [preference] is the account's current view selection. Only
     * [ViewingPreference.MINE] has a request-level mechanism (`onlyMine=true`);
     * the following/followers scopes are applied by the server from the saved
     * preference, which is why [setViewingPreference] must succeed before a
     * refresh can show a different view.
     */
    suspend fun refreshFeed(
        preference: ViewingPreference = ViewingPreference.DEFAULT,
    ): ApiResult<String?>

    /**
     * Fetches the page that follows [cursor] and appends it to the cached feed.
     * [cursor] is the opaque token a previous [refreshFeed]/[loadMoreFeed]
     * returned and is handed to the API verbatim — never construct or parse one.
     * [preference] must match the one the page chain started under. Returns the
     * cursor for the page after this one, or null at the end.
     */
    suspend fun loadMoreFeed(
        cursor: String,
        preference: ViewingPreference = ViewingPreference.DEFAULT,
    ): ApiResult<String?>

    /**
     * The account's saved feed view preference, read from `viewingPreference` on
     * `GET /api/user`. Seeds the in-feed switcher so Android opens on whatever the
     * web was last set to.
     */
    suspend fun getViewingPreference(): ApiResult<ViewingPreference>

    /**
     * Saves [preference] to the account with a partial `PATCH /api/user/update`,
     * so the choice persists and the web agrees. Returns the value the server
     * reports as saved. Callers must reload the feed from the top afterwards: the
     * server applies this preference when it builds the feed.
     */
    suspend fun setViewingPreference(preference: ViewingPreference): ApiResult<ViewingPreference>

    /**
     * Creates a new top-level message and caches it. Optionally attaches already
     * uploaded [imageUrls] / [videoUrls], defers publishing to [scheduledAt]
     * (ISO-8601), and cross-posts to the already-linked networks named by
     * [crossPost] (InterlinedList-only when [CrossPostSelection.NONE]). A scheduled
     * message does not enter the feed cache. Returns the created message plus any
     * per-network cross-post delivery statuses the endpoint reported.
     *
     * [visibility] is always sent explicitly so the server default never silently
     * decides; callers seed it from [getDefaultVisibility] and let the user
     * override it per message.
     *
     * [pushedMessageId] re-shares another message: with [content] this is a
     * **quote**, and [pushMessage] is the no-comment **push**. Amplifying someone
     * else's message is always public, so a non-null [pushedMessageId] overrides
     * [visibility] with [MessageVisibility.PUSH_OR_QUOTE].
     *
     * [tags] are sent as `tags[]`, verbatim: free-form labels that may contain
     * spaces and punctuation. An empty list omits the field entirely.
     */
    suspend fun createMessage(
        content: String,
        imageUrls: List<String> = emptyList(),
        videoUrls: List<String> = emptyList(),
        scheduledAt: String? = null,
        crossPost: CrossPostSelection = CrossPostSelection.NONE,
        visibility: MessageVisibility = MessageVisibility.PUBLIC,
        pushedMessageId: String? = null,
        tags: List<String> = emptyList(),
    ): ApiResult<CreatedMessage>

    /**
     * Tag suggestions for the prefix the user is typing, from
     * `GET /api/tags/autocomplete` (query parameter **`q`**).
     *
     * The server does the matching: a **case-insensitive literal prefix** over
     * existing public tags. The result is returned in the server's order and is
     * never re-filtered or fuzzy-matched here — doing so would show (or hide)
     * suggestions the server never chose. Network-only: suggestions are not cached.
     */
    suspend fun autocompleteTags(query: String, limit: Int = TAG_SUGGESTION_LIMIT): ApiResult<List<TagSuggestion>>

    /**
     * Pushes (reposts) [messageId] as-is: posts `pushedMessageId` with **no**
     * content, always publicly. A quote — the same repost with the user's own
     * note — goes through [createMessage] with a `pushedMessageId` instead.
     *
     * Callers must only offer this for a message whose [Message.canBePushed] is
     * true; the server rejects the rest and the failure is surfaced verbatim.
     */
    suspend fun pushMessage(messageId: String): ApiResult<CreatedMessage>

    /**
     * The account's default post visibility, read from `defaultPubliclyVisible`
     * on `GET /api/user`. Seeds the composer's Public/Private toggle.
     */
    suspend fun getDefaultVisibility(): ApiResult<MessageVisibility>

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

    companion object {
        /** How many tag suggestions to ask for (server default 10, max 50). */
        const val TAG_SUGGESTION_LIMIT = 10
    }
}
