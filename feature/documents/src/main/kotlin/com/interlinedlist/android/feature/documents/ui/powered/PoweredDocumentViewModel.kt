package com.interlinedlist.android.feature.documents.ui.powered

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.ai.data.AiRepository
import com.interlinedlist.android.feature.ai.domain.AiAvailability
import com.interlinedlist.android.feature.ai.domain.AiCreated
import com.interlinedlist.android.feature.ai.domain.AiFeature
import com.interlinedlist.android.feature.ai.domain.AiGate
import com.interlinedlist.android.feature.ai.domain.AiPreview
import com.interlinedlist.android.feature.ai.domain.AiQuota
import com.interlinedlist.android.feature.ai.domain.AiSuggestInput
import com.interlinedlist.android.feature.ai.ui.AiPreviewSession
import com.interlinedlist.android.feature.ai.ui.AiPreviewState
import com.interlinedlist.android.feature.ai.ui.toUserMessage
import com.interlinedlist.android.feature.documents.data.DocumentsRepository
import com.interlinedlist.android.feature.documents.data.ListSourcesRepository
import com.interlinedlist.android.feature.documents.domain.Document
import com.interlinedlist.android.feature.documents.domain.ListSource
import com.interlinedlist.android.feature.documents.ui.common.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

/** Which source picker is open over the form, if any. */
enum class SourcePicker { NONE, LISTS, DOCUMENTS }

/**
 * Everything the Powered Document surface renders. [preview] and [availability]
 * are overlaid from `:feature:ai` (the preview session and the gate); the rest is
 * the form the user is filling in.
 */
data class PoweredDocumentUiState(
    val availability: AiAvailability = AiAvailability.Unknown,
    val preview: AiPreviewState = AiPreviewState.Idle,
    val mode: PoweredDocumentMode = PoweredDocumentMode.ARTICLE,
    val instruction: String = "",
    val researchUrl: String = "",
    val selectedList: ListSource? = null,
    val selectedDocument: Document? = null,
    val listSources: List<ListSource> = emptyList(),
    val isLoadingListSources: Boolean = false,
    val picker: SourcePicker = SourcePicker.NONE,
    val documentQuery: String = "",
    val isSearchingDocuments: Boolean = false,
    val documentResults: List<Document> = emptyList(),
    /** The title the user may adjust before confirming; seeded from the draft. */
    val editedTitle: String = "",
    /** Refused locally — no request was issued and no quota was spent. */
    val validationMessage: String? = null,
    /** A failure loading the source pickers (not an AI failure). */
    val sourceErrorMessage: String? = null,
    val createdDocumentId: String? = null,
) {
    /** The single check that decides whether any AI control is drawn at all. */
    val isAiEnabled: Boolean get() = availability.isEnabled

    /** Today's remaining allowance, when the server reported one. */
    val quota: AiQuota? get() = (availability as? AiAvailability.Available)?.quota

    val isQuotaExhausted: Boolean get() = availability.isQuotaExhausted

    val isBusy: Boolean get() = preview.isBusy

    /** The draft awaiting approval. Non-null means **nothing has been written yet**. */
    val previewing: AiPreview? get() = (preview as? AiPreviewState.Previewing)?.preview

    /** Whether the mode's required source (or topic) is present and usable. */
    val hasRequiredSource: Boolean
        get() = when (mode) {
            PoweredDocumentMode.ARTICLE -> instruction.isNotBlank()
            PoweredDocumentMode.FROM_LIST -> selectedList != null
            PoweredDocumentMode.FROM_ARTICLE -> selectedDocument != null
            PoweredDocumentMode.RESEARCH_URL -> ResearchUrl.isValid(researchUrl)
        }

    val canSuggest: Boolean
        get() = isAiEnabled && !isBusy && previewing == null && hasRequiredSource

    val canConfirm: Boolean get() = isAiEnabled && previewing != null && !isBusy

    /**
     * One message for the screen. An AI failure wins — it is the most recent
     * thing that happened — and each `code` keeps its own wording, so a spent
     * daily allowance reads as the daily limit and not as a generic error.
     */
    val errorMessage: String?
        get() = (preview as? AiPreviewState.Failed)?.error?.toUserMessage()
            ?: sourceErrorMessage
            ?: validationMessage
}

/**
 * Drives the Powered Document flow: pick a mode and its source, preview the draft
 * via `/suggest`, and only then persist it via `/generate`.
 *
 * The preview → confirm rule is not re-implemented here: [AiPreviewSession] owns
 * it, and `/generate` is unreachable except through a `ConfirmedPreview` that only
 * an on-screen preview can produce. Backing out ([discard]) therefore cannot write
 * anything, by construction.
 */
@HiltViewModel
class PoweredDocumentViewModel @Inject constructor(
    private val documentsRepository: DocumentsRepository,
    private val listSourcesRepository: ListSourcesRepository,
    aiRepository: AiRepository,
    private val aiGate: AiGate,
) : ViewModel() {

    private val session = AiPreviewSession(aiRepository, aiGate, viewModelScope)

    private val _uiState = MutableStateFlow(PoweredDocumentUiState())
    val uiState: StateFlow<PoweredDocumentUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        // The control that opened this screen is already gated on `Available`;
        // re-reading keeps the quota figure honest if the screen is revisited.
        viewModelScope.launch { aiGate.ensureResolved() }
        viewModelScope.launch {
            aiGate.availability.collect { availability ->
                _uiState.update { it.copy(availability = availability) }
            }
        }
        viewModelScope.launch {
            session.state.collect { preview ->
                _uiState.update { it.copy(preview = preview) }
                onPreviewStateChanged(preview)
            }
        }
    }

    // --- Mode and inputs ---------------------------------------------------

    fun selectMode(mode: PoweredDocumentMode) {
        if (_uiState.value.mode == mode) return
        _uiState.update { it.copy(mode = mode, validationMessage = null, sourceErrorMessage = null) }
    }

    fun onInstructionChange(text: String) =
        _uiState.update { it.copy(instruction = text, validationMessage = null) }

    fun onResearchUrlChange(text: String) =
        _uiState.update { it.copy(researchUrl = text, validationMessage = null) }

    fun onEditedTitleChange(text: String) = _uiState.update { it.copy(editedTitle = text) }

    // --- Source pickers ----------------------------------------------------

    /**
     * Opens the list picker, loading the user's lists on first use. The lists
     * live in another feature module, so they are read here through this module's
     * own one-endpoint `/api/lists` client rather than by depending on
     * `:feature:lists`.
     */
    fun openListPicker() {
        _uiState.update { it.copy(picker = SourcePicker.LISTS, sourceErrorMessage = null) }
        if (_uiState.value.listSources.isNotEmpty() || _uiState.value.isLoadingListSources) return
        _uiState.update { it.copy(isLoadingListSources = true) }
        viewModelScope.launch {
            when (val result = listSourcesRepository.getListSources()) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isLoadingListSources = false, listSources = result.data)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(
                        isLoadingListSources = false,
                        sourceErrorMessage = result.error.toUserMessage(),
                    )
                }
            }
        }
    }

    fun selectList(list: ListSource) = _uiState.update {
        it.copy(selectedList = list, picker = SourcePicker.NONE, validationMessage = null)
    }

    /** Opens the shared documents search picker (the browser's own overlay). */
    fun openDocumentPicker() = _uiState.update {
        it.copy(
            picker = SourcePicker.DOCUMENTS,
            documentQuery = "",
            documentResults = emptyList(),
            sourceErrorMessage = null,
        )
    }

    fun onDocumentQueryChange(query: String) {
        _uiState.update { it.copy(documentQuery = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(documentResults = emptyList(), isSearchingDocuments = false) }
            return
        }
        _uiState.update { it.copy(isSearchingDocuments = true) }
        searchJob = viewModelScope.launch {
            when (val result = documentsRepository.searchDocuments(query.trim())) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isSearchingDocuments = false, documentResults = result.data)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(
                        isSearchingDocuments = false,
                        sourceErrorMessage = result.error.toUserMessage(),
                    )
                }
            }
        }
    }

    /** Picks the source document by the id the shared picker reports. */
    fun selectDocument(documentId: String) {
        val picked = _uiState.value.documentResults.firstOrNull { it.id == documentId } ?: return
        _uiState.update {
            it.copy(
                selectedDocument = picked,
                picker = SourcePicker.NONE,
                validationMessage = null,
            )
        }
    }

    fun closePicker() = _uiState.update { it.copy(picker = SourcePicker.NONE) }

    // --- Preview and confirm ----------------------------------------------

    /**
     * Runs `/suggest`. Refuses locally — issuing no request, and spending none of
     * the daily allowance — when AI is not available, the mode's source is missing
     * or unusable, or the instruction is over the endpoint's word cap.
     */
    fun suggest() {
        if (!aiGate.availability.value.isEnabled) return
        val state = _uiState.value
        val context = contextFor(state) ?: return
        val input = state.instruction.trim().ifBlank { state.mode.defaultInstruction.orEmpty() }
        if (wordCount(input) > MAX_INPUT_WORDS) {
            refuse("Shorten the instruction to $MAX_INPUT_WORDS words or fewer.")
            return
        }
        _uiState.update { it.copy(validationMessage = null, sourceErrorMessage = null, editedTitle = "") }
        session.suggest(AiFeature.POWERED_DOCUMENT, AiSuggestInput(input = input, context = context))
    }

    /** Approves the draft on screen and persists it via `/generate`. */
    fun confirm() {
        val preview = (session.state.value as? AiPreviewState.Previewing)?.preview ?: return
        val title = _uiState.value.editedTitle.trim()
        val edited = title
            .takeIf { it.isNotBlank() && it != preview.artifact.documentTitle }
            ?.let { preview.artifact.withTitle(it) }
        session.confirm(edited = edited)
    }

    /**
     * Backs out of the draft. Nothing was ever written — `/suggest` persists
     * nothing — so this only clears the screen.
     */
    fun discard() {
        session.discard()
        _uiState.update { it.copy(editedTitle = "", validationMessage = null, sourceErrorMessage = null) }
    }

    // --- Internals ---------------------------------------------------------

    private fun onPreviewStateChanged(state: AiPreviewState) {
        when (state) {
            is AiPreviewState.Previewing ->
                _uiState.update { it.copy(editedTitle = state.preview.artifact.documentTitle.orEmpty()) }

            is AiPreviewState.Generated -> {
                val created = state.generation.created
                if (created is AiCreated.DocumentCreated) {
                    _uiState.update { it.copy(createdDocumentId = created.documentId) }
                    // The new document exists server-side only; pull it into the cache
                    // so the browser shows it the moment the user lands back there.
                    viewModelScope.launch { documentsRepository.refreshTree() }
                }
            }

            else -> Unit
        }
    }

    /**
     * Builds `context` for the chosen mode, or refuses with a message naming what
     * is missing. Only ids and the URL are sent: the server resolves the source
     * itself, under the owning user.
     */
    private fun contextFor(state: PoweredDocumentUiState): JsonObject? = when (state.mode) {
        PoweredDocumentMode.ARTICLE ->
            if (state.instruction.isBlank()) {
                refuse("Describe what the article should be about.")
            } else {
                buildJsonObject { put("mode", state.mode.apiValue) }
            }

        PoweredDocumentMode.FROM_LIST -> state.selectedList?.let { list ->
            buildJsonObject {
                put("mode", state.mode.apiValue)
                put("listId", list.id)
            }
        } ?: refuse("Choose a list to write from.")

        PoweredDocumentMode.FROM_ARTICLE -> state.selectedDocument?.let { document ->
            buildJsonObject {
                put("mode", state.mode.apiValue)
                put("documentId", document.id)
            }
        } ?: refuse("Choose a document to write from.")

        PoweredDocumentMode.RESEARCH_URL -> ResearchUrl.normalise(state.researchUrl)?.let { url ->
            buildJsonObject {
                put("mode", state.mode.apiValue)
                put("url", url)
            }
        } ?: refuse("Enter a full http:// or https:// web address.")
    }

    /** Records why nothing was sent, and returns null so the caller bails out. */
    private fun refuse(message: String): JsonObject? {
        _uiState.update { it.copy(validationMessage = message) }
        return null
    }

    private fun wordCount(text: String): Int =
        text.trim().split(WHITESPACE).count { it.isNotEmpty() }

    private companion object {
        /** `powered_document` caps `input` at 500 words server-side. */
        const val MAX_INPUT_WORDS = 500
        val WHITESPACE = Regex("\\s+")
    }
}
