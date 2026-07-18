package com.interlinedlist.android.feature.messages.ui

import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.messages.data.MessagesRepository
import com.interlinedlist.android.feature.messages.domain.Message
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

    var refreshResult: ApiResult<Boolean> = ApiResult.Success(false)
    var loadMoreResult: ApiResult<Boolean> = ApiResult.Success(false)
    var createResult: ApiResult<Message>? = null
    var fetchResult: ApiResult<Message>? = null
    var refreshRepliesResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var postReplyResult: ApiResult<Message>? = null
    var setDugResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var deleteResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var searchResult: ApiResult<List<Message>> = ApiResult.Success(emptyList())

    var refreshCount = 0
    var loadMoreCount = 0
    var lastSetDug: Pair<String, Boolean>? = null
    var deletedIds = mutableListOf<String>()

    fun emitFeed(messages: List<Message>) { feed.value = messages }
    fun emitReplies(parentId: String, messages: List<Message>) {
        replies.value = replies.value + (parentId to messages)
    }
    fun emitMessage(message: Message) { single.value = single.value + (message.id to message) }

    override fun observeFeed(): Flow<List<Message>> = feed

    override fun observeReplies(messageId: String): Flow<List<Message>> =
        replies.map { it[messageId].orEmpty() }

    override fun observeMessage(messageId: String): Flow<Message?> =
        single.map { it[messageId] }

    override suspend fun refreshFeed(): ApiResult<Boolean> {
        refreshCount++
        return refreshResult
    }

    override suspend fun loadMoreFeed(currentCount: Int): ApiResult<Boolean> {
        loadMoreCount++
        return loadMoreResult
    }

    override suspend fun createMessage(content: String): ApiResult<Message> =
        createResult ?: ApiResult.Failure(AppError.Unknown("createResult not set"))

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
) = Message(
    id = id,
    content = content,
    authorId = "u1",
    authorUsername = "adron",
    authorDisplayName = "Adron",
    authorAvatarUrl = null,
    createdAt = null,
    digCount = digCount,
    replyCount = replyCount,
    dugByMe = dugByMe,
    parentId = parentId,
    mine = mine,
)
