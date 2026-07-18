package com.interlinedlist.android.feature.messages.ui.detail

import androidx.lifecycle.SavedStateHandle
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

/** Nav argument key for the message id the detail screen renders. */
const val MESSAGE_ID_ARG = "messageId"

/** Detail screen state: the message, its replies, and transient flags. */
data class MessageDetailUiState(
    val message: Message? = null,
    val replies: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val subscriptionRequired: Boolean = false,
    val replyText: String = "",
    val isPostingReply: Boolean = false,
) {
    val canReply: Boolean get() = replyText.isNotBlank() && !isPostingReply
}

private data class DetailTransientState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val subscriptionRequired: Boolean = false,
    val replyText: String = "",
    val isPostingReply: Boolean = false,
)

@HiltViewModel
class MessageDetailViewModel @Inject constructor(
    private val repository: MessagesRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val messageId: String = requireNotNull(savedStateHandle[MESSAGE_ID_ARG]) {
        "MessageDetailViewModel requires a '$MESSAGE_ID_ARG' nav argument"
    }

    private val transient = MutableStateFlow(DetailTransientState())

    val uiState: StateFlow<MessageDetailUiState> =
        combine(
            repository.observeMessage(messageId),
            repository.observeReplies(messageId),
            transient,
        ) { message, replies, t ->
            MessageDetailUiState(
                message = message,
                replies = replies,
                isLoading = t.isLoading,
                errorMessage = t.errorMessage,
                subscriptionRequired = t.subscriptionRequired,
                replyText = t.replyText,
                isPostingReply = t.isPostingReply,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MessageDetailUiState(isLoading = true),
        )

    init {
        load()
    }

    fun load() {
        transient.update { it.copy(isLoading = true, errorMessage = null, subscriptionRequired = false) }
        viewModelScope.launch {
            val messageResult = repository.fetchMessage(messageId)
            if (messageResult is ApiResult.Failure) {
                transient.update { it.copy(isLoading = false).withError(messageResult.error) }
                return@launch
            }
            when (val repliesResult = repository.refreshReplies(messageId)) {
                is ApiResult.Success -> transient.update { it.copy(isLoading = false) }
                is ApiResult.Failure -> transient.update {
                    it.copy(isLoading = false).withError(repliesResult.error)
                }
            }
        }
    }

    fun onReplyTextChange(value: String) = transient.update { it.copy(replyText = value) }

    fun onDig() {
        val message = uiState.value.message ?: return
        viewModelScope.launch {
            val result = repository.setDug(message.id, dug = !message.dugByMe)
            if (result is ApiResult.Failure) {
                transient.update { it.withError(result.error) }
            }
        }
    }

    fun postReply() {
        val text = transient.value.replyText.trim()
        if (text.isBlank()) return
        transient.update { it.copy(isPostingReply = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.postReply(parentId = messageId, content = text)) {
                is ApiResult.Success -> transient.update {
                    it.copy(isPostingReply = false, replyText = "")
                }
                is ApiResult.Failure -> transient.update {
                    it.copy(isPostingReply = false).withError(result.error)
                }
            }
        }
    }

    fun dismissError() = transient.update { it.copy(errorMessage = null, subscriptionRequired = false) }

    private fun DetailTransientState.withError(error: AppError): DetailTransientState =
        copy(errorMessage = error.toUserMessage(), subscriptionRequired = error.isSubscriptionGate)
}
