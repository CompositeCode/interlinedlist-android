package com.interlinedlist.android.feature.directmessages.ui.thread

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.directmessages.data.DirectMessagesRepository
import com.interlinedlist.android.feature.directmessages.navigation.DirectMessagesDestinations
import com.interlinedlist.android.feature.directmessages.ui.model.MessageBubble
import com.interlinedlist.android.feature.directmessages.ui.model.toBubble
import com.interlinedlist.android.feature.directmessages.ui.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/** UI state for a single conversation thread. */
data class ThreadUiState(
    val username: String,
    val messages: List<MessageBubble> = emptyList(),
    val draft: String = "",
    val pendingImageUrls: List<String> = emptyList(),
    val isSending: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    val canSend: Boolean
        get() = (draft.isNotBlank() || pendingImageUrls.isNotEmpty()) && !isSending
    val isEmpty: Boolean get() = messages.isEmpty() && !isLoading
}

@HiltViewModel
class ThreadViewModel(
    private val repository: DirectMessagesRepository,
    private val username: String,
    private val pollIntervalMillis: Long = DEFAULT_POLL_INTERVAL_MILLIS,
) : ViewModel() {

    /** Hilt entry point: pulls the username from the nav arguments. */
    @Inject
    constructor(
        repository: DirectMessagesRepository,
        savedStateHandle: SavedStateHandle,
    ) : this(
        repository = repository,
        username = requireNotNull(
            savedStateHandle.get<String>(DirectMessagesDestinations.ARG_USERNAME),
        ) { "Thread route requires a '${DirectMessagesDestinations.ARG_USERNAME}' argument" },
        pollIntervalMillis = DEFAULT_POLL_INTERVAL_MILLIS,
    )

    private val currentUserId = repository.currentUserId

    private val _uiState = MutableStateFlow(ThreadUiState(username = username, isLoading = true))
    val uiState: StateFlow<ThreadUiState> = _uiState.asStateFlow()

    private var pollJob: Job? = null

    init {
        // Room is the source of truth: mirror cached messages into the state.
        viewModelScope.launch {
            repository.observeThread(username).collect { messages ->
                _uiState.update { state ->
                    state.copy(messages = messages.map { it.toBubble(currentUserId) })
                }
            }
        }
        refresh()
    }

    /** Full reload of the thread (also marks received-unread messages read). */
    fun refresh() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.refreshThread(username)) {
                is ApiResult.Success -> _uiState.update { it.copy(isLoading = false) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    /**
     * Starts near-real-time polling of the open thread, mirroring the
     * notifications poller. Call from the screen's lifecycle (started) and pair
     * with [stopPolling] (stopped) so we don't poll a backgrounded thread.
     * Idempotent: repeated calls do not stack pollers.
     */
    fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(pollIntervalMillis)
                repository.pollThreadUpdates(username)
            }
        }
    }

    /** Stops the poll loop started by [startPolling]. */
    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    fun onDraftChange(value: String) = _uiState.update { it.copy(draft = value, errorMessage = null) }

    fun attachImage(url: String) =
        _uiState.update { it.copy(pendingImageUrls = it.pendingImageUrls + url) }

    fun removeImage(url: String) =
        _uiState.update { it.copy(pendingImageUrls = it.pendingImageUrls - url) }

    /** Optimistic send: the repository echoes the message locally before the network call. */
    fun send() {
        val current = _uiState.value
        val body = current.draft.trim()
        if (body.isBlank() && current.pendingImageUrls.isEmpty()) return
        val images = current.pendingImageUrls
        // Clear the composer immediately for a snappy feel; restore on failure.
        _uiState.update { it.copy(draft = "", pendingImageUrls = emptyList(), isSending = true) }
        viewModelScope.launch {
            when (val result = repository.send(username, body, images)) {
                is ApiResult.Success -> _uiState.update { it.copy(isSending = false) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(
                        isSending = false,
                        draft = body,
                        pendingImageUrls = images,
                        errorMessage = result.error.toUserMessage(),
                    )
                }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }

    companion object {
        const val DEFAULT_POLL_INTERVAL_MILLIS = 5_000L
    }
}
