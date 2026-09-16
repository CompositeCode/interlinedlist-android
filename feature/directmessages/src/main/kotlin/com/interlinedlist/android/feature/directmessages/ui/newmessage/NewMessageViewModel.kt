package com.interlinedlist.android.feature.directmessages.ui.newmessage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.directmessages.data.DirectMessagesRepository
import com.interlinedlist.android.feature.directmessages.data.Recipient
import com.interlinedlist.android.feature.directmessages.ui.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The mutually exclusive surfaces the recipient picker can show. Keeping these
 * distinct stops a successful-but-empty response (the normal state for a new
 * account, see [RecipientRuleCopy]) from reading like a failure or a blank list.
 */
enum class NewMessageContent {
    /** The recipient set is still being fetched. */
    Loading,

    /** The fetch failed — a real error the user can retry. */
    Error,

    /** Loaded fine, but the user may not message anyone yet. */
    NoRecipients,

    /** There are recipients, but the current query matches none of them. */
    NoMatches,

    /** Recipients to pick from. */
    Recipients,
}

/** UI state for the recipient picker used to start a new conversation. */
data class NewMessageUiState(
    val query: String = "",
    val recipients: List<Recipient> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
) {
    /** Recipients matching [query] against username and display name. */
    val filtered: List<Recipient>
        get() = if (query.isBlank()) {
            recipients
        } else {
            val q = query.trim().lowercase()
            recipients.filter { r ->
                r.username.lowercase().contains(q) ||
                    r.displayName?.lowercase()?.contains(q) == true
            }
        }

    /** Which of the picker's surfaces to render; see [NewMessageContent]. */
    val content: NewMessageContent
        get() = when {
            isLoading -> NewMessageContent.Loading
            errorMessage != null -> NewMessageContent.Error
            recipients.isEmpty() -> NewMessageContent.NoRecipients
            filtered.isEmpty() -> NewMessageContent.NoMatches
            else -> NewMessageContent.Recipients
        }
}

@HiltViewModel
class NewMessageViewModel @Inject constructor(
    private val repository: DirectMessagesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewMessageUiState())
    val uiState: StateFlow<NewMessageUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.recipients()) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isLoading = false, recipients = result.data)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    fun onQueryChange(value: String) = _uiState.update { it.copy(query = value) }
}
