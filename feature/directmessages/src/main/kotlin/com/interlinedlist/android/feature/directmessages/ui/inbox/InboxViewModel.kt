package com.interlinedlist.android.feature.directmessages.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.directmessages.data.Conversation
import com.interlinedlist.android.feature.directmessages.data.DirectMessagesRepository
import com.interlinedlist.android.feature.directmessages.ui.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** UI state for the Conversations / Inbox list. */
data class InboxUiState(
    val conversations: List<Conversation> = emptyList(),
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val nextCursor: String? = null,
    val errorMessage: String? = null,
) {
    /** Number of conversations with an unread received message, for the badge. */
    val unreadConversationCount: Int get() = conversations.count { it.hasUnread }
    val isEmpty: Boolean get() = conversations.isEmpty()
    val canLoadMore: Boolean get() = nextCursor != null && !isLoadingMore && !isRefreshing
}

@HiltViewModel
class InboxViewModel @Inject constructor(
    private val repository: DirectMessagesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InboxUiState())
    val uiState: StateFlow<InboxUiState> = _uiState.asStateFlow()

    init {
        // Room is the source of truth: mirror cached conversations into the state.
        viewModelScope.launch {
            repository.observeConversations().collect { conversations ->
                _uiState.update { it.copy(conversations = conversations) }
            }
        }
        refresh()
    }

    /** Pull-to-refresh: reloads the first inbox page. */
    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.refreshInbox(cursor = null)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isRefreshing = false, nextCursor = result.data)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isRefreshing = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    /** Loads the next inbox page using the stored cursor, if any. */
    fun loadMore() {
        val cursor = _uiState.value.nextCursor ?: return
        if (_uiState.value.isLoadingMore) return
        _uiState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            when (val result = repository.refreshInbox(cursor = cursor)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isLoadingMore = false, nextCursor = result.data)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isLoadingMore = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
}
