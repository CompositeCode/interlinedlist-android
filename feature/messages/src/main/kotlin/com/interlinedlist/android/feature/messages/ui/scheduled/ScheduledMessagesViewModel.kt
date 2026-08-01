package com.interlinedlist.android.feature.messages.ui.scheduled

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

/** State for the Scheduled messages screen: the cached scheduled list + flags. */
data class ScheduledUiState(
    val messages: List<Message> = emptyList(),
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val subscriptionRequired: Boolean = false,
) {
    val isEmpty: Boolean get() = messages.isEmpty()
}

private data class ScheduledTransientState(
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val subscriptionRequired: Boolean = false,
)

@HiltViewModel
class ScheduledMessagesViewModel @Inject constructor(
    private val repository: MessagesRepository,
) : ViewModel() {

    private val transient = MutableStateFlow(ScheduledTransientState())

    /** Room is the source of truth: scheduled rows come from the cache Flow. */
    val uiState: StateFlow<ScheduledUiState> =
        combine(repository.observeScheduled(), transient) { messages, t ->
            ScheduledUiState(
                messages = messages,
                isRefreshing = t.isRefreshing,
                errorMessage = t.errorMessage,
                subscriptionRequired = t.subscriptionRequired,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ScheduledUiState(),
        )

    init {
        refresh()
    }

    fun refresh() {
        transient.update { it.copy(isRefreshing = true, errorMessage = null, subscriptionRequired = false) }
        viewModelScope.launch {
            when (val result = repository.refreshScheduled()) {
                is ApiResult.Success -> transient.update { it.copy(isRefreshing = false) }
                is ApiResult.Failure -> transient.update {
                    it.copy(isRefreshing = false).withError(result.error)
                }
            }
        }
    }

    /** Cancels (deletes) a scheduled message; the cache Flow drops it. */
    fun cancel(message: Message) {
        viewModelScope.launch {
            val result = repository.cancelScheduled(message.id)
            if (result is ApiResult.Failure) {
                transient.update { it.withError(result.error) }
            }
        }
    }

    fun dismissError() = transient.update { it.copy(errorMessage = null, subscriptionRequired = false) }

    private fun ScheduledTransientState.withError(error: AppError): ScheduledTransientState =
        copy(errorMessage = error.toUserMessage(), subscriptionRequired = error.isSubscriptionGate)
}
