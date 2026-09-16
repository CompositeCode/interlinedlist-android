package com.interlinedlist.android.feature.messages.ui

import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.core.model.ViewingPreference
import com.interlinedlist.android.feature.messages.data.MessagesRepository
import com.interlinedlist.android.feature.messages.domain.CreatedMessage
import com.interlinedlist.android.feature.messages.domain.CrossPostSelection
import com.interlinedlist.android.feature.messages.domain.CrossPostStatus
import com.interlinedlist.android.feature.messages.domain.LinkedNetwork
import com.interlinedlist.android.feature.messages.domain.Message
import com.interlinedlist.android.feature.messages.domain.MessageVisibility
import com.interlinedlist.android.feature.messages.domain.PushedMessage
import com.interlinedlist.android.feature.messages.domain.ReportReason
import com.interlinedlist.android.feature.messages.domain.TagSuggestion
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * A configurable in-memory [MessagesRepository] for ViewModel tests. Feed/reply
 * state is exposed as Flows (source of truth), and each operation's result can be
 * pre-set to drive success/failure paths.
 */
class FakeMessagesRepository : MessagesRepository {

    private val feed = MutableStateFlow<List<Message>>(emptyList())
    private val replies = MutableStateFlow<Map<String, List<Message>>>(emptyMap())
    private val single = MutableStateFlow<Map<String, Message>>(emptyMap())
    private val scheduled = MutableStateFlow<List<Message>>(emptyList())

    /** The cursor a refresh hands back; null means "end of the feed". */
    var refreshResult: ApiResult<String?> = ApiResult.Success(null)
    /** The cursor a page-append hands back; null means "end of the feed". */
    var loadMoreResult: ApiResult<String?> = ApiResult.Success(null)
    var createResult: ApiResult<Message>? = null
    /** What [pushMessage] answers with; falls back to [createResult]'s message. */
    var pushResult: ApiResult<Message>? = null
    /** Cross-post statuses returned alongside a successful [createResult]. */
    var createCrossPosts: List<CrossPostStatus> = emptyList()
    var linkedNetworksResult: ApiResult<List<LinkedNetwork>> = ApiResult.Success(emptyList())
    /** The account's default post visibility, as read from `GET /api/user`. */
    var defaultVisibilityResult: ApiResult<MessageVisibility> = ApiResult.Success(MessageVisibility.PUBLIC)
    var fetchResult: ApiResult<Message>? = null
    var refreshRepliesResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var postReplyResult: ApiResult<Message>? = null
    var setDugResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var deleteResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var editResult: ApiResult<Message>? = null
    var blockResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var muteResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var reportUserResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var searchResult: ApiResult<List<Message>> = ApiResult.Success(emptyList())
    var uploadImageResult: ApiResult<String> = ApiResult.Success("https://cdn/image.png")
    var uploadVideoResult: ApiResult<String> = ApiResult.Success("https://cdn/video.mp4")
    var refreshScheduledResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var cancelScheduledResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var reportResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var metadataResult: ApiResult<Message>? = null
    /** Per-query canned autocomplete answers; anything else falls back below. */
    val autocompleteResponses = mutableMapOf<String, ApiResult<List<TagSuggestion>>>()
    var autocompleteResult: ApiResult<List<TagSuggestion>> = ApiResult.Success(emptyList())
    /** The account's saved feed preference, as read from `GET /api/user`. */
    var viewingPreferenceResult: ApiResult<ViewingPreference> = ApiResult.Success(ViewingPreference.ALL)
    /** What the `PATCH /api/user/update` of the preference answers with. */
    var setViewingPreferenceResult: ApiResult<ViewingPreference>? = null

    var refreshCount = 0
    var loadMoreCount = 0
    /** Every cursor handed to [loadMoreFeed], in order. */
    val loadMoreCursors = mutableListOf<String>()
    /** The preference each [refreshFeed] ran under, in order. */
    val refreshPreferences = mutableListOf<ViewingPreference>()
    /** The preference each [loadMoreFeed] ran under, in order. */
    val loadMorePreferences = mutableListOf<ViewingPreference>()
    /** Every preference [setViewingPreference] was asked to PATCH, in order. */
    val savedViewingPreferences = mutableListOf<ViewingPreference>()
    var lastSetDug: Pair<String, Boolean>? = null
    var deletedIds = mutableListOf<String>()
    var lastCreate: CreateArgs? = null
    /** Every message id handed to [pushMessage], in order. */
    val pushedMessageIds = mutableListOf<String>()
    var uploadedImages = 0
    var uploadedVideos = 0
    var refreshScheduledCount = 0
    var cancelledScheduledIds = mutableListOf<String>()
    var lastReport: ReportArgs? = null
    var metadataFetchedIds = mutableListOf<String>()
    var lastEdit: Pair<String, String>? = null
    var blockedUsernames = mutableListOf<String>()
    var mutedUsernames = mutableListOf<String>()
    var lastReportUser: ReportUserArgs? = null
    /** Every query [autocompleteTags] was asked for, in order. */
    val autocompleteQueries = mutableListOf<String>()
    /** Queries whose in-flight call was cancelled before it could answer. */
    val cancelledAutocompleteQueries = mutableListOf<String>()
    /** Queries the fake actually answered (as opposed to never getting to). */
    val completedAutocompleteQueries = mutableListOf<String>()
    private val autocompleteGates = mutableMapOf<String, TagGate>()

    /**
     * Makes the lookup for [query] hang until [releaseAutocomplete], so a test can
     * hold a request "in flight" across the next keystroke.
     *
     * [ignoreCancellation] models the nastier race: the server had already
     * answered, so the response lands **even though** the caller was cancelled.
     * Only a stale-query guard can discard that one.
     */
    fun gateAutocomplete(query: String, ignoreCancellation: Boolean = false) {
        autocompleteGates[query] = TagGate(CompletableDeferred(), ignoreCancellation)
    }

    /** Lets a gated lookup answer. */
    fun releaseAutocomplete(query: String) {
        autocompleteGates[query]?.signal?.complete(Unit)
    }

    private class TagGate(val signal: CompletableDeferred<Unit>, val ignoreCancellation: Boolean)

    /** Snapshot of the arguments passed to the last [createMessage] call. */
    data class CreateArgs(
        val content: String,
        val imageUrls: List<String>,
        val videoUrls: List<String>,
        val scheduledAt: String?,
        val crossPost: CrossPostSelection = CrossPostSelection.NONE,
        val visibility: MessageVisibility = MessageVisibility.PUBLIC,
        val pushedMessageId: String? = null,
        val tags: List<String> = emptyList(),
    )

    /** Snapshot of the arguments passed to the last [report] call. */
    data class ReportArgs(val messageId: String, val reason: ReportReason, val detail: String?)

    /** Snapshot of the arguments passed to the last [reportUser] call. */
    data class ReportUserArgs(val username: String, val reason: ReportReason, val detail: String?)

    fun emitFeed(messages: List<Message>) { feed.value = messages }
    fun emitReplies(parentId: String, messages: List<Message>) {
        replies.value = replies.value + (parentId to messages)
    }
    fun emitMessage(message: Message) { single.value = single.value + (message.id to message) }
    fun emitScheduled(messages: List<Message>) { scheduled.value = messages }

    override fun observeFeed(): Flow<List<Message>> = feed

    override fun observeReplies(messageId: String): Flow<List<Message>> =
        replies.map { it[messageId].orEmpty() }

    override fun observeMessage(messageId: String): Flow<Message?> =
        single.map { it[messageId] }

    override fun observeScheduled(): Flow<List<Message>> = scheduled

    override suspend fun refreshFeed(preference: ViewingPreference): ApiResult<String?> {
        refreshCount++
        refreshPreferences += preference
        return refreshResult
    }

    override suspend fun loadMoreFeed(cursor: String, preference: ViewingPreference): ApiResult<String?> {
        loadMoreCount++
        loadMoreCursors += cursor
        loadMorePreferences += preference
        return loadMoreResult
    }

    override suspend fun getViewingPreference(): ApiResult<ViewingPreference> = viewingPreferenceResult

    override suspend fun setViewingPreference(
        preference: ViewingPreference,
    ): ApiResult<ViewingPreference> {
        savedViewingPreferences += preference
        return setViewingPreferenceResult ?: ApiResult.Success(preference)
    }

    override suspend fun createMessage(
        content: String,
        imageUrls: List<String>,
        videoUrls: List<String>,
        scheduledAt: String?,
        crossPost: CrossPostSelection,
        visibility: MessageVisibility,
        pushedMessageId: String?,
        tags: List<String>,
    ): ApiResult<CreatedMessage> {
        lastCreate = CreateArgs(
            content, imageUrls, videoUrls, scheduledAt, crossPost, visibility, pushedMessageId, tags,
        )
        return when (val result = createResult) {
            is ApiResult.Success -> ApiResult.Success(CreatedMessage(result.data, createCrossPosts))
            is ApiResult.Failure -> result
            null -> ApiResult.Failure(AppError.Unknown("createResult not set"))
        }
    }

    override suspend fun pushMessage(messageId: String): ApiResult<CreatedMessage> {
        pushedMessageIds += messageId
        return when (val result = pushResult ?: createResult) {
            is ApiResult.Success -> ApiResult.Success(CreatedMessage(result.data, emptyList()))
            is ApiResult.Failure -> result
            null -> ApiResult.Failure(AppError.Unknown("pushResult not set"))
        }
    }

    override suspend fun getLinkedNetworks(): ApiResult<List<LinkedNetwork>> = linkedNetworksResult

    override suspend fun getDefaultVisibility(): ApiResult<MessageVisibility> = defaultVisibilityResult

    override suspend fun uploadImage(bytes: ByteArray, fileName: String, mimeType: String): ApiResult<String> {
        uploadedImages++
        return uploadImageResult
    }

    override suspend fun uploadVideo(bytes: ByteArray, fileName: String, mimeType: String): ApiResult<String> {
        uploadedVideos++
        return uploadVideoResult
    }

    override suspend fun fetchMessage(messageId: String): ApiResult<Message> =
        fetchResult ?: ApiResult.Failure(AppError.Unknown("fetchResult not set"))

    override suspend fun refreshReplies(messageId: String): ApiResult<Unit> = refreshRepliesResult

    override suspend fun postReply(parentId: String, content: String): ApiResult<Message> =
        postReplyResult ?: ApiResult.Failure(AppError.Unknown("postReplyResult not set"))

    override suspend fun setDug(messageId: String, dug: Boolean): ApiResult<Unit> {
        lastSetDug = messageId to dug
        return setDugResult
    }

    override suspend fun deleteMessage(messageId: String): ApiResult<Unit> {
        deletedIds += messageId
        return deleteResult
    }

    override suspend fun editMessage(messageId: String, content: String): ApiResult<Message> {
        lastEdit = messageId to content
        val result = editResult ?: ApiResult.Failure(AppError.Unknown("editResult not set"))
        if (result is ApiResult.Success) {
            // Reflect the edit into the observable feed/message so the UI re-emits.
            feed.value = feed.value.map { if (it.id == messageId) result.data else it }
            single.value = single.value + (messageId to result.data)
        }
        return result
    }

    override suspend fun refreshScheduled(): ApiResult<Unit> {
        refreshScheduledCount++
        return refreshScheduledResult
    }

    override suspend fun cancelScheduled(messageId: String): ApiResult<Unit> {
        cancelledScheduledIds += messageId
        return cancelScheduledResult
    }

    override suspend fun report(messageId: String, reason: ReportReason, detail: String?): ApiResult<Unit> {
        lastReport = ReportArgs(messageId, reason, detail)
        return reportResult
    }

    override suspend fun blockUser(username: String): ApiResult<Unit> {
        blockedUsernames += username
        val result = blockResult
        if (result is ApiResult.Success) {
            // Mirror the repository's hide-on-block behaviour for ViewModel tests.
            feed.value = feed.value.filterNot { it.authorUsername == username }
        }
        return result
    }

    override suspend fun muteUser(username: String): ApiResult<Unit> {
        mutedUsernames += username
        val result = muteResult
        if (result is ApiResult.Success) {
            feed.value = feed.value.filterNot { it.authorUsername == username }
        }
        return result
    }

    override suspend fun reportUser(username: String, reason: ReportReason, detail: String?): ApiResult<Unit> {
        lastReportUser = ReportUserArgs(username, reason, detail)
        return reportUserResult
    }

    override suspend fun fetchMetadata(messageId: String): ApiResult<Message> {
        metadataFetchedIds += messageId
        return metadataResult ?: ApiResult.Failure(AppError.Unknown("metadataResult not set"))
    }

    override suspend fun autocompleteTags(query: String, limit: Int): ApiResult<List<TagSuggestion>> {
        autocompleteQueries += query
        autocompleteGates[query]?.let { gate ->
            if (gate.ignoreCancellation) {
                // The request is already past the point of no return: it answers
                // whatever the caller does.
                withContext(NonCancellable) { gate.signal.await() }
            } else {
                try {
                    gate.signal.await()
                } catch (cancellation: CancellationException) {
                    cancelledAutocompleteQueries += query
                    throw cancellation
                }
            }
        }
        completedAutocompleteQueries += query
        return autocompleteResponses[query] ?: autocompleteResult
    }

    override suspend fun search(query: String): ApiResult<List<Message>> = searchResult
}

/** Builds a sample [LinkedNetwork] for tests. */
fun sampleNetwork(
    id: String,
    provider: String,
    providerUsername: String = "handle",
) = LinkedNetwork(
    id = id,
    provider = provider,
    providerUsername = providerUsername,
)

/** Builds a sample [Message] for tests. */
fun sampleMessage(
    id: String = "1",
    content: String = "hello",
    mine: Boolean = false,
    dugByMe: Boolean = false,
    digCount: Int = 0,
    replyCount: Int = 0,
    parentId: String? = null,
    imageUrls: List<String> = emptyList(),
    videoUrls: List<String> = emptyList(),
    scheduledAt: String? = null,
    authorUsername: String = "adron",
    editedAt: String? = null,
    publiclyVisible: Boolean = true,
    pushCount: Int = 0,
    pushedMessageId: String? = null,
    pushedMessage: PushedMessage? = null,
    tags: List<String> = emptyList(),
) = Message(
    id = id,
    content = content,
    authorId = "u1",
    authorUsername = authorUsername,
    authorDisplayName = "Adron",
    authorAvatarUrl = null,
    createdAt = null,
    digCount = digCount,
    replyCount = replyCount,
    dugByMe = dugByMe,
    parentId = parentId,
    mine = mine,
    imageUrls = imageUrls,
    videoUrls = videoUrls,
    scheduledAt = scheduledAt,
    editedAt = editedAt,
    publiclyVisible = publiclyVisible,
    pushCount = pushCount,
    pushedMessageId = pushedMessageId,
    pushedMessage = pushedMessage,
    tags = tags,
)

/** Builds a sample embedded original (the `pushedMessage` on a push/quote). */
fun samplePushedMessage(
    id: String = "orig",
    content: String = "the original post",
    authorUsername: String = "quinn",
    authorDisplayName: String? = "Quinn",
) = PushedMessage(
    id = id,
    content = content,
    authorUsername = authorUsername,
    authorDisplayName = authorDisplayName,
    authorAvatarUrl = null,
    createdAt = null,
)
