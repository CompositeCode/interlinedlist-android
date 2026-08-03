package com.interlinedlist.android.feature.documents.ui.templates

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.documents.data.DocumentsRepository
import com.interlinedlist.android.feature.documents.domain.DocumentTemplate
import com.interlinedlist.android.feature.documents.ui.common.isSubscriptionGate
import com.interlinedlist.android.feature.documents.ui.common.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Optional nav argument: the folder a template-derived document should be created in
 * (absent / the synthetic root == unfiled). The templates surface can be opened from
 * a folder level to seed a document directly there.
 */
const val TEMPLATES_TARGET_FOLDER_ARG = "targetFolderId"

/** UI state for the templates surface. */
data class DocumentTemplatesUiState(
    val templates: List<DocumentTemplate> = emptyList(),
    val isLoading: Boolean = true,
    val isSeeding: Boolean = false,
    val errorMessage: String? = null,
    val subscriptionRequired: Boolean = false,
) {
    /** No templates yet (and nothing in flight) — offer seeding the defaults. */
    val canSeedDefaults: Boolean
        get() = templates.isEmpty() && !isLoading && !isSeeding && !subscriptionRequired
}

/**
 * Drives the templates surface: lists the user's template documents, offers a
 * "Seed default templates" action (auto-offered when the list is empty), and creates
 * a new document from a chosen template. Seeding refreshes the list so the newly
 * created templates render immediately.
 */
@HiltViewModel
class DocumentTemplatesViewModel @Inject constructor(
    private val repository: DocumentsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** Where template-derived documents should land (null == root/unfiled). */
    private val targetFolderId: String? =
        savedStateHandle.get<String>(TEMPLATES_TARGET_FOLDER_ARG)

    private val _uiState = MutableStateFlow(DocumentTemplatesUiState())
    val uiState: StateFlow<DocumentTemplatesUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            applyTemplatesResult(repository.getTemplates()) { it.copy(isLoading = false) }
        }
    }

    /** Seeds the built-in default templates, then renders the refreshed list. */
    fun seedDefaults() {
        if (_uiState.value.isSeeding) return
        _uiState.update { it.copy(isSeeding = true, errorMessage = null) }
        viewModelScope.launch {
            applyTemplatesResult(repository.seedDefaultTemplates()) { it.copy(isSeeding = false) }
        }
    }

    /** Creates a document from [template]; invokes [onCreated] with its id to open it. */
    fun createFromTemplate(template: DocumentTemplate, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            when (val result = repository.createFromTemplate(template.id, targetFolderId)) {
                is ApiResult.Success -> onCreated(result.data.id)
                is ApiResult.Failure -> _uiState.update {
                    it.copy(errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }

    private inline fun applyTemplatesResult(
        result: ApiResult<List<DocumentTemplate>>,
        crossinline finalize: (DocumentTemplatesUiState) -> DocumentTemplatesUiState,
    ) {
        when (result) {
            is ApiResult.Success -> _uiState.update {
                finalize(it.copy(templates = result.data, subscriptionRequired = false))
            }
            is ApiResult.Failure -> _uiState.update {
                finalize(
                    it.copy(
                        errorMessage = result.error.toUserMessage(),
                        subscriptionRequired = result.error.isSubscriptionGate,
                    ),
                )
            }
        }
    }
}
