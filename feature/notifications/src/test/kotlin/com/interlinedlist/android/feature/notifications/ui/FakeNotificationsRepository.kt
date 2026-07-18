package com.interlinedlist.android.feature.notifications.ui

import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.notifications.data.NotificationsRepository
import com.interlinedlist.android.feature.notifications.domain.Notification
import com.interlinedlist.android.feature.notifications.domain.NotificationTarget
import com.interlinedlist.android.feature.notifications.domain.NotificationType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * A configurable in-memory [NotificationsRepository] for ViewModel tests. The list
 * and unread count are exposed as Flows (source of truth), and each operation's
 * result can be pre-set to drive success/failure paths.
 */
class FakeNotificationsRepository : NotificationsRepository {

    private val notifications = MutableStateFlow<List<Notification>>(emptyList())

    var refreshResult: ApiResult<Boolean> = ApiResult.Success(false)
    var loadMoreResult: ApiResult<Boolean> = ApiResult.Success(false)
    var markReadResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var markAllReadResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var dismissResult: ApiResult<Unit> = ApiResult.Success(Unit)

    var refreshCount = 0
    var loadMoreCount = 0
    var markReadIds = mutableListOf<String>()
    var markAllReadCount = 0
    var dismissedIds = mutableListOf<String>()

    fun emit(items: List<Notification>) { notifications.value = items }

    override fun observeNotifications(): Flow<List<Notification>> = notifications

    override fun observeUnreadCount(): Flow<Int> =
        notifications.map { list -> list.count { !it.read } }

    override suspend fun refresh(): ApiResult<Boolean> {
        refreshCount++
        return refreshResult
    }

    override suspend fun loadMore(currentCount: Int): ApiResult<Boolean> {
        loadMoreCount++
        return loadMoreResult
    }

    override suspend fun markRead(id: String): ApiResult<Unit> {
        markReadIds += id
        if (markReadResult is ApiResult.Success) {
            notifications.value = notifications.value.map {
                if (it.id == id) it.copy(read = true) else it
            }
        }
        return markReadResult
    }

    override suspend fun markAllRead(): ApiResult<Unit> {
        markAllReadCount++
        if (markAllReadResult is ApiResult.Success) {
            notifications.value = notifications.value.map { it.copy(read = true) }
        }
        return markAllReadResult
    }

    override suspend fun dismiss(id: String): ApiResult<Unit> {
        dismissedIds += id
        if (dismissResult is ApiResult.Success) {
            notifications.value = notifications.value.filterNot { it.id == id }
        }
        return dismissResult
    }
}

/** Builds a sample [Notification] for tests. */
fun sampleNotification(
    id: String = "1",
    type: NotificationType = NotificationType.FOLLOW,
    subject: String = "Amy started following you",
    body: String? = null,
    read: Boolean = false,
    target: NotificationTarget? = null,
) = Notification(
    id = id,
    type = type,
    actor = null,
    subject = subject,
    body = body,
    createdAt = null,
    read = read,
    target = target,
)
