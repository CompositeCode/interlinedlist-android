package com.interlinedlist.android.feature.messages.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.messages.data.MessagesRepository
import com.interlinedlist.android.feature.messages.domain.Message
import com.interlinedlist.android.feature.messages.domain.ReportReason
import com.interlinedlist.android.feature.messages.ui.feed.ModerationAction
import com.interlinedlist.android.feature.messages.ui.feed.ModerationTarget
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
    /** The message (root or a reply) being reported, if any. */
    val reportTarget: Message? = null,
    val isReporting: Boolean = false,
    /** The message (root or a reply) being edited in-place, if any. */
    val editTarget: Message? = null,
    val editText: String = "",
    val isSavingEdit: Boolean = false,
    /** A pending author-moderation action awaiting confirmation, if any. */
    val moderationTarget: ModerationTarget? = null,
    val isModerating: Boolean = false,
) {
    val canReply: Boolean get() = replyText.isNotBlank() && !isPostingReply
    val canSaveEdit: Boolean get() = editText.isNotBlank() && !isSavingEdit
}

private data class DetailTransientState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val subscriptionRequired: Boolean = false,
    val replyText: String = "",
    val isPostingReply: Boolean = false,
    val reportTarget: Message? = null,
    val isReporting: Boolean = false,
    val editTarget: Message? = null,
    val editText: String = "",
    val isSavingEdit: Boolean = false,
    val moderationTarget: ModerationTarget? = null,
    val isModerating: Boolean = false,
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
                reportTarget = t.reportTarget,
                isReporting = t.isReporting,
                editTarget = t.editTarget,
                editText = t.editText,
                isSavingEdit = t.isSavingEdit,
                moderationTarget = t.moderationTarget,
                isModerating = t.isModerating,
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

    // --- report ------------------------------------------------------------

    fun openReport(message: Message) = transient.update { it.copy(reportTarget = message, errorMessage = null) }

    fun dismissReport() = transient.update { it.copy(reportTarget = null, isReporting = false) }

    fun submitReport(reason: ReportReason, detail: String) {
        val target = transient.value.reportTarget ?: return
        transient.update { it.copy(isReporting = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.report(target.id, reason, detail)) {
                is ApiResult.Success -> transient.update { it.copy(isReporting = false, reportTarget = null) }
                is ApiResult.Failure -> transient.update {
                    it.copy(isReporting = false, reportTarget = null).withError(result.error)
                }
            }
        }
    }

    // --- edit own message --------------------------------------------------

    fun openEdit(message: Message) = transient.update {
        it.copy(editTarget = message, editText = message.content, errorMessage = null)
    }

    fun onEditTextChange(value: String) = transient.update { it.copy(editText = value) }

    fun dismissEdit() = transient.update {
        it.copy(editTarget = null, editText = "", isSavingEdit = false)
    }

    fun saveEdit() {
        val target = transient.value.editTarget ?: return
        val text = transient.value.editText.trim()
        if (text.isBlank()) return
        transient.update { it.copy(isSavingEdit = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.editMessage(target.id, text)) {
                is ApiResult.Success -> transient.update {
                    it.copy(isSavingEdit = false, editTarget = null, editText = "")
                }
                is ApiResult.Failure -> transient.update {
                    it.copy(isSavingEdit = false).withError(result.error)
                }
            }
        }
    }

    // --- author moderation -------------------------------------------------

    fun openModeration(message: Message, action: ModerationAction) = transient.update {
        it.copy(moderationTarget = ModerationTarget(message, action), errorMessage = null)
    }

    fun dismissModeration() = transient.update {
        it.copy(moderationTarget = null, isModerating = false)
    }

    fun confirmModeration(reason: ReportReason? = null, detail: String = "") {
        val target = transient.value.moderationTarget ?: return
        transient.update { it.copy(isModerating = true, errorMessage = null) }
        viewModelScope.launch {
            val result = when (target.action) {
                ModerationAction.BLOCK -> repository.blockUser(target.username)
                ModerationAction.MUTE -> repository.muteUser(target.username)
                ModerationAction.REPORT ->
                    repository.reportUser(target.username, reason ?: ReportReason.OTHER, detail)
            }
            when (result) {
                is ApiResult.Success -> transient.update {
                    it.copy(isModerating = false, moderationTarget = null)
                }
                is ApiResult.Failure -> transient.update {
                    it.copy(isModerating = false, moderationTarget = null).withError(result.error)
                }
            }
        }
    }

    // --- link metadata -----------------------------------------------------

    /** Fetches link-preview metadata for the current message; cache re-emits it. */
    fun onFetchMetadata() {
        val current = uiState.value.message ?: return
        viewModelScope.launch {
            val result = repository.fetchMetadata(current.id)
            if (result is ApiResult.Failure) {
                transient.update { it.withError(result.error) }
            }
        }
    }

    fun dismissError() = transient.update { it.copy(errorMessage = null, subscriptionRequired = false) }

    private fun DetailTransientState.withError(error: AppError): DetailTransientState =
        copy(errorMessage = error.toUserMessage(), subscriptionRequired = error.isSubscriptionGate)
}
