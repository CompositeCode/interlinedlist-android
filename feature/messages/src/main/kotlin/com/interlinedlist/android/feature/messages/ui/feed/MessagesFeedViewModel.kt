package com.interlinedlist.android.feature.messages.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.messages.data.MessagesRepository
import com.interlinedlist.android.feature.messages.domain.Message
import com.interlinedlist.android.feature.messages.ui.isSubscriptionGate
import com.interlinedlist.android.feature.messages.ui.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Feed screen state: the cached messages plus transient network/compose flags. */
data class MessagesFeedUiState(
    val messages: List<Message> = emptyList(),
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    val errorMessage: String? = null,
    /** True when the failure is a subscription gate — render an upsell instead. */
    val subscriptionRequired: Boolean = false,
    val isComposeOpen: Boolean = false,
    val composeText: String = "",
    val isPosting: Boolean = false,
) {
    val isEmpty: Boolean get() = messages.isEmpty()
    val canPost: Boolean get() = composeText.isNotBlank() && !isPosting
}

/** Transient (non-cached) UI flags kept separate from the Room-backed message list. */
private data class FeedTransientState(
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    val errorMessage: String? = null,
    val subscriptionRequired: Boolean = false,
    val isComposeOpen: Boolean = false,
    val composeText: String = "",
    val isPosting: Boolean = false,
)

@HiltViewModel
class MessagesFeedViewModel @Inject constructor(
    private val repository: MessagesRepository,
) : ViewModel() {

    private val transient = MutableStateFlow(FeedTransientState())

    /**
     * Room is the source of truth: the feed list comes from the cache Flow and is
     * combined with transient flags into a single [MessagesFeedUiState].
     */
    val uiState: StateFlow<MessagesFeedUiState> =
        combine(repository.observeFeed(), transient) { messages, t ->
            MessagesFeedUiState(
                messages = messages,
                isRefreshing = t.isRefreshing,
                isLoadingMore = t.isLoadingMore,
                canLoadMore = t.canLoadMore,
                errorMessage = t.errorMessage,
                subscriptionRequired = t.subscriptionRequired,
                isComposeOpen = t.isComposeOpen,
                composeText = t.composeText,
                isPosting = t.isPosting,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MessagesFeedUiState(),
        )

    init {
        refresh()
    }

    fun refresh() {
        transient.update { it.copy(isRefreshing = true, errorMessage = null, subscriptionRequired = false) }
        viewModelScope.launch {
            when (val result = repository.refreshFeed()) {
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
            when (val result = repository.loadMoreFeed(currentCount = current.messages.size)) {
                is ApiResult.Success -> transient.update {
                    it.copy(isLoadingMore = false, canLoadMore = result.data)
                }
                is ApiResult.Failure -> transient.update {
                    it.copy(isLoadingMore = false).withError(result.error)
                }
            }
        }
    }

    fun onDig(message: Message) {
        viewModelScope.launch {
            // Repository updates the cache optimistically and rolls back on failure.
            val result = repository.setDug(message.id, dug = !message.dugByMe)
            if (result is ApiResult.Failure) {
                transient.update { it.withError(result.error) }
            }
        }
    }

    fun onDelete(message: Message) {
        viewModelScope.launch {
            val result = repository.deleteMessage(message.id)
            if (result is ApiResult.Failure) {
                transient.update { it.withError(result.error) }
            }
        }
    }

    // --- compose sheet -----------------------------------------------------

    fun openCompose() = transient.update { it.copy(isComposeOpen = true, errorMessage = null) }

    fun dismissCompose() = transient.update { it.copy(isComposeOpen = false, composeText = "") }

    fun onComposeTextChange(value: String) = transient.update { it.copy(composeText = value) }

    fun post() {
        val text = transient.value.composeText.trim()
        if (text.isBlank()) return
        transient.update { it.copy(isPosting = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.createMessage(text)) {
                is ApiResult.Success -> transient.update {
                    it.copy(isPosting = false, isComposeOpen = false, composeText = "")
                }
                is ApiResult.Failure -> transient.update {
                    it.copy(isPosting = false).withError(result.error)
                }
            }
        }
    }

    fun dismissError() = transient.update { it.copy(errorMessage = null, subscriptionRequired = false) }

    private fun FeedTransientState.withError(error: AppError?): FeedTransientState =
        if (error == null) this
        else copy(errorMessage = error.toUserMessage(), subscriptionRequired = error.isSubscriptionGate)
}
