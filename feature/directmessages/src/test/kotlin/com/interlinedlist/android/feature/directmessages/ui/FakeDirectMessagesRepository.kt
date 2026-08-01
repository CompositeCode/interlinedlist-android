package com.interlinedlist.android.feature.directmessages.ui

import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.directmessages.data.Conversation
import com.interlinedlist.android.feature.directmessages.data.DirectMessage
import com.interlinedlist.android.feature.directmessages.data.DirectMessagesRepository
import com.interlinedlist.android.feature.directmessages.data.Recipient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Configurable in-memory [DirectMessagesRepository] for ViewModel tests. Flows
 * are backed by [MutableStateFlow]s tests can push to; suspend calls return the
 * result the test primes and record that they ran.
 */
class FakeDirectMessagesRepository(
    override val currentUserId: String? = "me",
) : DirectMessagesRepository {

    val conversationsFlow = MutableStateFlow<List<Conversation>>(emptyList())
    val threadFlow = MutableStateFlow<List<DirectMessage>>(emptyList())

    var refreshInboxResult: ApiResult<String?> = ApiResult.Success(null)
    var refreshThreadResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var pollResult: ApiResult<Int> = ApiResult.Success(0)
    var sendResult: ApiResult<DirectMessage>? = null
    var markReadResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var trashResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var restoreResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var recipientsResult: ApiResult<List<Recipient>> = ApiResult.Success(emptyList())
    var unreadResult: ApiResult<Int> = ApiResult.Success(0)

    var refreshInboxCount = 0
    var refreshThreadCount = 0
    var pollCount = 0
    val sentBodies = mutableListOf<String>()

    override fun observeConversations(): Flow<List<Conversation>> = conversationsFlow.asStateFlow()
    override fun observeThread(username: String): Flow<List<DirectMessage>> = threadFlow.asStateFlow()

    override suspend fun refreshInbox(cursor: String?): ApiResult<String?> {
        refreshInboxCount++
        return refreshInboxResult
    }

    override suspend fun refreshThread(username: String): ApiResult<Unit> {
        refreshThreadCount++
        return refreshThreadResult
    }

    override suspend fun pollThreadUpdates(username: String): ApiResult<Int> {
        pollCount++
        return pollResult
    }

    override suspend fun send(
        username: String,
        body: String,
        imageUrls: List<String>,
    ): ApiResult<DirectMessage> {
        sentBodies += body
        return sendResult ?: ApiResult.Success(
            DirectMessage(
                id = "srv-$body", conversationUsername = username, senderId = currentUserId ?: "me",
                recipientId = "other", body = body, imageUrls = imageUrls,
                createdAt = "2026-07-31T12:00:00Z", createdAtMillis = 1L, readAt = null,
                pending = false,
            ),
        )
    }

    override suspend fun markRead(id: String): ApiResult<Unit> = markReadResult
    override suspend fun trash(id: String): ApiResult<Unit> = trashResult
    override suspend fun restore(id: String, username: String): ApiResult<Unit> = restoreResult
    override suspend fun recipients(): ApiResult<List<Recipient>> = recipientsResult
    override suspend fun unreadCount(): ApiResult<Int> = unreadResult

    fun failEverythingWith(error: AppError) {
        refreshInboxResult = ApiResult.Failure(error)
        refreshThreadResult = ApiResult.Failure(error)
    }
}
