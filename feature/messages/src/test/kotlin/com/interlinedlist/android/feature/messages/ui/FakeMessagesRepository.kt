package com.interlinedlist.android.feature.messages.ui

import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.messages.data.MessagesRepository
import com.interlinedlist.android.feature.messages.domain.Message
import com.interlinedlist.android.feature.messages.domain.ReportReason
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

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

    var refreshResult: ApiResult<Boolean> = ApiResult.Success(false)
    var loadMoreResult: ApiResult<Boolean> = ApiResult.Success(false)
    var createResult: ApiResult<Message>? = null
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

    var refreshCount = 0
    var loadMoreCount = 0
    var lastSetDug: Pair<String, Boolean>? = null
    var deletedIds = mutableListOf<String>()
    var lastCreate: CreateArgs? = null
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

    /** Snapshot of the arguments passed to the last [createMessage] call. */
    data class CreateArgs(
        val content: String,
        val imageUrls: List<String>,
        val videoUrls: List<String>,
        val scheduledAt: String?,
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

    override suspend fun refreshFeed(): ApiResult<Boolean> {
        refreshCount++
        return refreshResult
    }

    override suspend fun loadMoreFeed(currentCount: Int): ApiResult<Boolean> {
        loadMoreCount++
        return loadMoreResult
    }

    override suspend fun createMessage(
        content: String,
        imageUrls: List<String>,
        videoUrls: List<String>,
        scheduledAt: String?,
    ): ApiResult<Message> {
        lastCreate = CreateArgs(content, imageUrls, videoUrls, scheduledAt)
        return createResult ?: ApiResult.Failure(AppError.Unknown("createResult not set"))
    }

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

    override suspend fun search(query: String): ApiResult<List<Message>> = searchResult
}

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
)
