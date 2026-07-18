package com.interlinedlist.android.feature.integrations.ui.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.integrations.data.IntegrationsRepository
import com.interlinedlist.android.feature.integrations.domain.ExportType
import com.interlinedlist.android.feature.integrations.ui.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * UI state for the export screen. [downloading] holds the export currently being
 * fetched (null when idle) so the row can show a spinner and the others disable.
 */
data class ExportUiState(
    val downloading: ExportType? = null,
    val errorMessage: String? = null,
) {
    fun isDownloading(type: ExportType): Boolean = downloading == type
    val isBusy: Boolean get() = downloading != null
}

/** A downloaded CSV ready to be handed to the share sheet — a one-shot event. */
data class ExportReady(val file: File)

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val repository: IntegrationsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    // Buffered channel so a file-ready event survives brief config-change gaps.
    private val _ready = Channel<ExportReady>(Channel.BUFFERED)
    val ready = _ready.receiveAsFlow()

    /** Downloads [type] to cache; on success emits a [ready] event to share it. */
    fun export(type: ExportType) {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(downloading = type, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.downloadExport(type)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(downloading = null) }
                    _ready.send(ExportReady(result.data))
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(downloading = null, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
}
