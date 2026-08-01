package com.interlinedlist.android.feature.documents.ui.presence

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.documents.data.DocumentsRepository
import com.interlinedlist.android.feature.documents.domain.Presence
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

/** Nav argument key the presence indicator reads its document id from. */
const val PRESENCE_DOCUMENT_ID_ARG = "documentId"

/** UI state for the lightweight presence indicator. */
data class DocumentPresenceUiState(
    val participants: List<Presence> = emptyList(),
) {
    /** People here other than a self-entry, if the server includes one — capped for the avatar row. */
    val count: Int get() = participants.size
}

/**
 * Lightweight presence: while a document is open, sends a heartbeat on an interval
 * (`POST /presence`) and exposes who else is here; on leave it stops and sends
 * `DELETE /presence`. Full live-cursor CRDT is intentionally out of scope.
 */
@HiltViewModel
class DocumentPresenceViewModel @Inject constructor(
    private val repository: DocumentsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val documentId: String = requireNotNull(savedStateHandle[PRESENCE_DOCUMENT_ID_ARG]) {
        "DocumentPresenceViewModel requires a '$PRESENCE_DOCUMENT_ID_ARG' nav argument"
    }

    private val _uiState = MutableStateFlow(DocumentPresenceUiState())
    val uiState: StateFlow<DocumentPresenceUiState> = _uiState.asStateFlow()

    private var heartbeatJob: Job? = null

    /** Begins heartbeating (idempotent — a second call is ignored while active). */
    fun start() {
        if (heartbeatJob?.isActive == true) return
        heartbeatJob = viewModelScope.launch {
            while (isActive) {
                when (val result = repository.sendPresence(documentId)) {
                    is ApiResult.Success -> _uiState.update { it.copy(participants = result.data) }
                    is ApiResult.Failure -> Unit // Transient — the next beat retries.
                }
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    /** Stops heartbeating and leaves the document. */
    fun stop() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        _uiState.update { it.copy(participants = emptyList()) }
        viewModelScope.launch { repository.leavePresence(documentId) }
    }

    override fun onCleared() {
        super.onCleared()
        if (heartbeatJob != null) stop()
    }

    companion object {
        /** How often the heartbeat is refreshed while a document is open. */
        const val HEARTBEAT_INTERVAL_MS = 20_000L
    }
}
