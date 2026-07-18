package com.interlinedlist.android.feature.messages.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.messages.data.MessagesRepository
import com.interlinedlist.android.feature.messages.domain.Message
import com.interlinedlist.android.feature.messages.domain.ReportReason
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

/** A pending media attachment being uploaded, or already uploaded, for a compose. */
data class PendingAttachment(
    val fileName: String,
    val isVideo: Boolean,
    /** Set once the upload finishes; null while [isUploading]. */
    val hostedUrl: String? = null,
    val isUploading: Boolean = true,
)

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
    /** Media attached to the in-progress compose. */
    val attachments: List<PendingAttachment> = emptyList(),
    /** Optional future send time (ISO-8601) for the in-progress compose. */
    val scheduledAt: String? = null,
    /** The message currently being reported (drives the report dialog), if any. */
    val reportTarget: Message? = null,
    val isReporting: Boolean = false,
) {
    val isEmpty: Boolean get() = messages.isEmpty()
    val hasAttachments: Boolean get() = attachments.isNotEmpty()
    val isUploading: Boolean get() = attachments.any { it.isUploading }
    val isScheduled: Boolean get() = scheduledAt != null
    val canPost: Boolean
        get() = (composeText.isNotBlank() || attachments.any { it.hostedUrl != null }) &&
            !isPosting && !isUploading
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
    val attachments: List<PendingAttachment> = emptyList(),
    val scheduledAt: String? = null,
    val reportTarget: Message? = null,
    val isReporting: Boolean = false,
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
                attachments = t.attachments,
                scheduledAt = t.scheduledAt,
                reportTarget = t.reportTarget,
                isReporting = t.isReporting,
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

    fun dismissCompose() = transient.update {
        it.copy(isComposeOpen = false, composeText = "", attachments = emptyList(), scheduledAt = null)
    }

    fun onComposeTextChange(value: String) = transient.update { it.copy(composeText = value) }

    /** Sets (or clears with null) the future send time for the in-progress compose. */
    fun onScheduleChange(isoTimestamp: String?) = transient.update { it.copy(scheduledAt = isoTimestamp) }

    /**
     * Uploads a picked media file and attaches it to the compose. [bytes] and the
     * file metadata come from the platform picker at the UI layer, keeping this
     * ViewModel free of Android URI/ContentResolver dependencies.
     */
    fun onAttachMedia(bytes: ByteArray, fileName: String, mimeType: String, isVideo: Boolean) {
        val placeholder = PendingAttachment(fileName = fileName, isVideo = isVideo)
        transient.update { it.copy(attachments = it.attachments + placeholder, errorMessage = null) }
        viewModelScope.launch {
            val result = if (isVideo) {
                repository.uploadVideo(bytes, fileName, mimeType)
            } else {
                repository.uploadImage(bytes, fileName, mimeType)
            }
            when (result) {
                is ApiResult.Success -> transient.update { state ->
                    state.copy(
                        attachments = state.attachments.map {
                            if (it === placeholder || (it.fileName == fileName && it.isUploading)) {
                                it.copy(hostedUrl = result.data, isUploading = false)
                            } else {
                                it
                            }
                        },
                    )
                }
                is ApiResult.Failure -> transient.update { state ->
                    // Drop the failed placeholder and surface the error.
                    state.copy(
                        attachments = state.attachments.filterNot {
                            it.fileName == fileName && it.isUploading
                        },
                    ).withError(result.error)
                }
            }
        }
    }

    /** Removes a not-yet-posted attachment from the compose. */
    fun onRemoveAttachment(attachment: PendingAttachment) = transient.update {
        it.copy(attachments = it.attachments - attachment)
    }

    fun post() {
        val snapshot = transient.value
        val text = snapshot.composeText.trim()
        val ready = snapshot.attachments.mapNotNull { it.hostedUrl }
        if (text.isBlank() && ready.isEmpty()) return
        if (snapshot.attachments.any { it.isUploading }) return
        val images = snapshot.attachments.filterNot { it.isVideo }.mapNotNull { it.hostedUrl }
        val videos = snapshot.attachments.filter { it.isVideo }.mapNotNull { it.hostedUrl }
        transient.update { it.copy(isPosting = true, errorMessage = null) }
        viewModelScope.launch {
            when (
                val result = repository.createMessage(
                    content = text,
                    imageUrls = images,
                    videoUrls = videos,
                    scheduledAt = snapshot.scheduledAt,
                )
            ) {
                is ApiResult.Success -> transient.update {
                    it.copy(
                        isPosting = false,
                        isComposeOpen = false,
                        composeText = "",
                        attachments = emptyList(),
                        scheduledAt = null,
                    )
                }
                is ApiResult.Failure -> transient.update {
                    it.copy(isPosting = false).withError(result.error)
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
                is ApiResult.Success -> transient.update {
                    it.copy(isReporting = false, reportTarget = null)
                }
                is ApiResult.Failure -> transient.update {
                    it.copy(isReporting = false, reportTarget = null).withError(result.error)
                }
            }
        }
    }

    // --- link metadata -----------------------------------------------------

    /** Fetches link-preview metadata for a message; the cache Flow re-emits it. */
    fun onFetchMetadata(message: Message) {
        viewModelScope.launch {
            val result = repository.fetchMetadata(message.id)
            if (result is ApiResult.Failure) {
                transient.update { it.withError(result.error) }
            }
        }
    }

    fun dismissError() = transient.update { it.copy(errorMessage = null, subscriptionRequired = false) }

    private fun FeedTransientState.withError(error: AppError?): FeedTransientState =
        if (error == null) this
        else copy(errorMessage = error.toUserMessage(), subscriptionRequired = error.isSubscriptionGate)
}
