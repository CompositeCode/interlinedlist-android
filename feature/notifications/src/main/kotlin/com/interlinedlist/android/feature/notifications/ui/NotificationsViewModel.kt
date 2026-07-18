package com.interlinedlist.android.feature.notifications.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.notifications.data.NotificationsRepository
import com.interlinedlist.android.feature.notifications.domain.Notification
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Notifications screen state: the cached list + unread count + transient flags. */
data class NotificationsUiState(
    val notifications: List<Notification> = emptyList(),
    val unreadCount: Int = 0,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    val errorMessage: String? = null,
    /** True when the failure is a subscription gate — render an upsell instead. */
    val subscriptionRequired: Boolean = false,
) {
    val isEmpty: Boolean get() = notifications.isEmpty()
    val hasUnread: Boolean get() = unreadCount > 0
}

/** Transient (non-cached) UI flags kept separate from the Room-backed list. */
private data class NotificationsTransientState(
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    val errorMessage: String? = null,
    val subscriptionRequired: Boolean = false,
)

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val repository: NotificationsRepository,
) : ViewModel() {

    private val transient = MutableStateFlow(NotificationsTransientState())

    /**
     * Room is the source of truth: the list and unread count come from the cache
     * Flows and are combined with transient flags into a single [NotificationsUiState].
     */
    val uiState: StateFlow<NotificationsUiState> =
        combine(
            repository.observeNotifications(),
            repository.observeUnreadCount(),
            transient,
        ) { notifications, unread, t ->
            NotificationsUiState(
                notifications = notifications,
                unreadCount = unread,
                isRefreshing = t.isRefreshing,
                isLoadingMore = t.isLoadingMore,
                canLoadMore = t.canLoadMore,
                errorMessage = t.errorMessage,
                subscriptionRequired = t.subscriptionRequired,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NotificationsUiState(),
        )

    init {
        refresh()
    }

    fun refresh() {
        transient.update { it.copy(isRefreshing = true, errorMessage = null, subscriptionRequired = false) }
        viewModelScope.launch {
            when (val result = repository.refresh()) {
                is ApiResult.Success -> transient.update {
                    it.copy(isRefreshing = false, canLoadMore = result.data)
                }
                is ApiResult.Failure -> transient.update {
                    it.copy(isRefreshing = false).withError(result.error)
                }
            }
        }
    }

    fun loadMore() {
        val current = uiState.value
        if (current.isLoadingMore || !current.canLoadMore) return
        transient.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            when (val result = repository.loadMore(currentCount = current.notifications.size)) {
                is ApiResult.Success -> transient.update {
                    it.copy(isLoadingMore = false, canLoadMore = result.data)
                }
                is ApiResult.Failure -> transient.update {
                    it.copy(isLoadingMore = false).withError(result.error)
                }
            }
        }
    }

    /** Marks a notification read (on tap). Repository updates the cache optimistically. */
    fun onOpen(notification: Notification) {
        if (notification.read) return
        viewModelScope.launch {
            val result = repository.markRead(notification.id)
            if (result is ApiResult.Failure) {
                transient.update { it.withError(result.error) }
            }
        }
    }

    /** Marks every notification read (top-bar action). */
    fun onMarkAllRead() {
        viewModelScope.launch {
            val result = repository.markAllRead()
            if (result is ApiResult.Failure) {
                transient.update { it.withError(result.error) }
            }
        }
    }

    /** Dismisses a notification (swipe or overflow). */
    fun onDismiss(notification: Notification) {
        viewModelScope.launch {
            val result = repository.dismiss(notification.id)
            if (result is ApiResult.Failure) {
                transient.update { it.withError(result.error) }
            }
        }
    }

    fun dismissError() = transient.update { it.copy(errorMessage = null, subscriptionRequired = false) }

    private fun NotificationsTransientState.withError(error: AppError?): NotificationsTransientState =
        if (error == null) this
        else copy(errorMessage = error.toUserMessage(), subscriptionRequired = error.isSubscriptionGate)
}
