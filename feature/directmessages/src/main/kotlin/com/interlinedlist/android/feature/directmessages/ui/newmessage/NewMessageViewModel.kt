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
    val isEmpty: Boolean get() = recipients.isEmpty() && !isLoading
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
