package com.interlinedlist.android.feature.lists.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.lists.data.GithubRepository
import com.interlinedlist.android.feature.lists.data.ListsRepository
import com.interlinedlist.android.feature.lists.domain.ListFreshness
import com.interlinedlist.android.feature.lists.domain.ListPresence
import com.interlinedlist.android.feature.lists.domain.ListRow
import com.interlinedlist.android.feature.lists.domain.ListSchema
import com.interlinedlist.android.feature.lists.domain.ListSummary
import com.interlinedlist.android.feature.lists.ui.isSubscriptionGate
import com.interlinedlist.android.feature.lists.ui.toUserMessage
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

/** UI state for the list detail screen (schema-driven table). */
data class ListDetailUiState(
    val summary: ListSummary? = null,
    val schema: ListSchema = ListSchema.EMPTY,
    val rows: List<ListRow> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val subscriptionRequired: Boolean = false,
    val isSaving: Boolean = false,
    val deleted: Boolean = false,
    val isRefreshing: Boolean = false,
    val refreshMessage: String? = null,
    val isEditingMetadata: Boolean = false,
    /** Ancestors of this list, root first — empty for a root list. */
    val breadcrumb: List<ListSummary> = emptyList(),
    /**
     * The issue number a newly added row will get, for a GitHub-backed list.
     * Null when unknown or not applicable — the row form simply omits the hint.
     */
    val nextIssueNumber: Int? = null,
    /** Other people currently in this list, from the freshness poll's presence half. */
    val presence: List<ListPresence> = emptyList(),
    /** The server's own verdict on whether anyone else is involved with this list. */
    val isCollaborative: Boolean = false,
) {
    val title: String get() = summary?.title.orEmpty()
    val isEmpty: Boolean get() = rows.isEmpty() && !isLoading && errorMessage == null

    /** True when this list mirrors a GitHub repository's issues. */
    val isGithubBacked: Boolean get() = summary?.isGithubBacked == true
}

/** The nav argument key the detail route reads its list id from. */
const val LIST_ID_ARG = "listId"

@HiltViewModel
class ListDetailViewModel @Inject constructor(
    private val repository: ListsRepository,
    private val githubRepository: GithubRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val listId: String = requireNotNull(savedStateHandle[LIST_ID_ARG]) {
        "ListDetailViewModel requires a '$LIST_ID_ARG' nav argument"
    }

    private val _uiState = MutableStateFlow(ListDetailUiState())
    val uiState: StateFlow<ListDetailUiState> = _uiState.asStateFlow()

    /** The freshness/presence loop. Non-null only while the list is on screen. */
    private var heartbeatJob: Job? = null

    /** The row the user is on, published to everyone else on the next beat. */
    private var focusedRowId: String? = null

    /** Polled time since the user last did anything, used to stop an idle screen. */
    private var idleMillis = 0L

    /** Whether the list is currently on screen; nothing may beat when it is not. */
    private var isOnScreen = false

    /** Set once the server says the list is not collaborative — then we stay quiet. */
    private var pollingDisabled = false

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null, subscriptionRequired = false) }
        viewModelScope.launch {
            when (val result = repository.getListDetail(listId)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            summary = result.data.summary,
                            schema = result.data.schema,
                            rows = result.data.rows,
                            isLoading = false,
                        )
                    }
                    loadBreadcrumb(result.data.summary)
                    loadNextIssueNumber(result.data.summary)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = result.error.toUserMessage(),
                        subscriptionRequired = result.error.isSubscriptionGate,
                    )
                }
            }
        }
    }

    // --- Collaborative freshness + presence --------------------------------

    /**
     * Starts the combined freshness poll and presence heartbeat for as long as the
     * list is on screen. Idempotent: a second call while one is running is ignored.
     *
     * The loop is deliberately frugal, because the server's own guidance is that the
     * database behind this endpoint bills for being awake:
     * - [ACTIVE_INTERVAL_MS] while the list is actually moving,
     * - [IDLE_INTERVAL_MS] when a beat brings no news (and after a failed beat),
     * - it stops outright once the server reports the list is not collaborative,
     * - and it stops after [MAX_IDLE_MS] without the user touching anything.
     */
    fun startHeartbeat() {
        isOnScreen = true
        if (heartbeatJob?.isActive == true) return
        // A fresh entry re-asks whether anyone else is on the list by now.
        pollingDisabled = false
        idleMillis = 0
        heartbeatJob = launchHeartbeat()
    }

    private fun launchHeartbeat(): Job = viewModelScope.launch {
        var interval = ACTIVE_INTERVAL_MS
        while (isActive) {
            when (val result = repository.pollFreshness(listId, rowVersions(), focusedRowId)) {
                is ApiResult.Success -> {
                    applyFreshness(result.data)
                    // Nobody else can see this list, so there is nothing to hear
                    // about: stop rather than keep a shared database awake.
                    if (!result.data.collaborative) {
                        pollingDisabled = true
                        return@launch
                    }
                    interval = if (result.data.hasChanges) ACTIVE_INTERVAL_MS else IDLE_INTERVAL_MS
                }
                // Transient — back off rather than retry hard; the screen still works.
                is ApiResult.Failure -> interval = IDLE_INTERVAL_MS
            }
            // A screen nobody has touched in ten minutes stops asking.
            if (idleMillis >= MAX_IDLE_MS) return@launch
            delay(interval)
            idleMillis += interval
        }
    }

    /**
     * Stops heartbeating when the list leaves the screen. There is no "leave" call
     * to make — the heartbeat is what keeps presence alive, so it simply expires
     * server-side — but the local presence is cleared so a returning screen never
     * shows who *was* here.
     */
    fun stopHeartbeat() {
        isOnScreen = false
        heartbeatJob?.cancel()
        heartbeatJob = null
        _uiState.update { it.copy(presence = emptyList()) }
    }

    /**
     * Publishes which row the user is on (null when they leave the editor). Also
     * counts as interaction, so opening a row revives an idled-out screen.
     */
    fun setFocusedRow(rowId: String?) {
        focusedRowId = rowId
        noteInteraction()
    }

    /**
     * Resets the idle timer; every user-initiated write calls it. Working in a list
     * that had gone quiet brings the heartbeat back — but only while the list is on
     * screen, and never on a list the server already said nobody else can see.
     */
    private fun noteInteraction() {
        idleMillis = 0
        if (isOnScreen && !pollingDisabled && heartbeatJob?.isActive != true) {
            heartbeatJob = launchHeartbeat()
        }
    }

    /** Versions of the rows on screen. A row with no known version is not asked about. */
    private fun rowVersions(): Map<String, Int> =
        _uiState.value.rows.mapNotNull { row -> row.version?.let { row.id to it } }.toMap()

    /**
     * Applies one poll: replaces exactly the rows the server says moved, drops the
     * ones it says are gone, and updates who is present. The whole table is never
     * refetched — that is the entire point of the endpoint.
     */
    private fun applyFreshness(freshness: ListFreshness) {
        _uiState.update { state ->
            val moved = freshness.changed.associateBy { it.id }
            val kept = state.rows
                .filterNot { it.id in freshness.deletedRowIds }
                .map { moved[it.id] ?: it }
            val keptIds = kept.mapTo(mutableSetOf()) { it.id }
            // A changed row we were not holding is appended rather than thrown away.
            val added = freshness.changed.filterNot { it.id in keptIds || it.id in freshness.deletedRowIds }
            state.copy(
                rows = kept + added,
                presence = freshness.presence,
                isCollaborative = freshness.collaborative,
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopHeartbeat()
    }

    fun addRow(values: Map<String, String>, onDone: () -> Unit = {}) {
        noteInteraction()
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            when (val result = repository.addRow(listId, values)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isSaving = false, rows = it.rows + result.data) }
                    onDone()
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isSaving = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    fun updateRow(rowId: String, values: Map<String, String>, onDone: () -> Unit = {}) {
        noteInteraction()
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            when (val result = repository.updateRow(listId, rowId, values)) {
                is ApiResult.Success -> {
                    _uiState.update { state ->
                        state.copy(
                            isSaving = false,
                            rows = state.rows.map { if (it.id == rowId) result.data else it },
                        )
                    }
                    onDone()
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isSaving = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    /**
     * Fetches the freshest copy of a single row from the server and merges it into
     * state, so a row-detail/edit view always seeds from current server data rather
     * than a possibly-stale cached page. Failures are silent — the cached row still
     * shows and edits still work.
     */
    fun loadRow(rowId: String) {
        viewModelScope.launch {
            when (val result = repository.getRow(listId, rowId)) {
                is ApiResult.Success -> _uiState.update { state ->
                    state.copy(rows = state.rows.map { if (it.id == rowId) result.data else it })
                }
                is ApiResult.Failure -> Unit
            }
        }
    }

    fun deleteRow(rowId: String) {
        noteInteraction()
        viewModelScope.launch {
            when (val result = repository.deleteRow(listId, rowId)) {
                is ApiResult.Success -> _uiState.update { state ->
                    state.copy(rows = state.rows.filterNot { it.id == rowId })
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    /**
     * Manually re-syncs a GitHub-backed list. On success the detail is reloaded so
     * the new rows appear, and a short summary is surfaced for the UI to toast.
     */
    fun refreshFromGithub() {
        if (_uiState.value.isRefreshing) return
        // The endpoint 400s on a local list; do not spend the request.
        if (!_uiState.value.isGithubBacked) return
        _uiState.update { it.copy(isRefreshing = true, errorMessage = null, refreshMessage = null) }
        viewModelScope.launch {
            when (val result = repository.refreshGithubList(listId)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            refreshMessage = result.data.summary
                                ?: result.data.message
                                ?: "List refreshed.",
                        )
                    }
                    // Pull the freshly-synced rows into view.
                    reload()
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isRefreshing = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    /** Reloads detail without toggling the top-level loading spinner (post-refresh). */
    private fun reload() {
        viewModelScope.launch {
            when (val result = repository.getListDetail(listId)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            summary = result.data.summary,
                            schema = result.data.schema,
                            rows = result.data.rows,
                        )
                    }
                    loadBreadcrumb(result.data.summary)
                    loadNextIssueNumber(result.data.summary)
                }
                is ApiResult.Failure -> Unit // Keep the existing rows; refresh already succeeded.
            }
        }
    }

    /**
     * For a GitHub-backed list, asks GitHub which number the next issue will take
     * so the add-row form can name the issue the user is about to open.
     *
     * Purely informational: a failure (or a repo the token cannot see) just leaves
     * the hint off rather than blocking a row the server would accept anyway.
     */
    private fun loadNextIssueNumber(summary: ListSummary) {
        val repo = summary.githubRepo?.takeIf { summary.isGithubBacked }
        if (repo == null) {
            _uiState.update { it.copy(nextIssueNumber = null) }
            return
        }
        viewModelScope.launch {
            when (val result = githubRepository.getNextIssueNumber(repo)) {
                is ApiResult.Success -> _uiState.update { it.copy(nextIssueNumber = result.data) }
                is ApiResult.Failure -> _uiState.update { it.copy(nextIssueNumber = null) }
            }
        }
    }

    /**
     * Walks the list's parent chain so the screen can show where it sits in the
     * tree. Purely navigational: a failure leaves the breadcrumb empty rather than
     * putting an error in front of a list that loaded fine.
     */
    private fun loadBreadcrumb(summary: ListSummary) {
        val parentId = summary.parentId
        if (parentId == null) {
            _uiState.update { it.copy(breadcrumb = emptyList()) }
            return
        }
        viewModelScope.launch {
            when (val result = repository.getParentChain(parentId)) {
                is ApiResult.Success -> _uiState.update { it.copy(breadcrumb = result.data) }
                is ApiResult.Failure -> _uiState.update { it.copy(breadcrumb = emptyList()) }
            }
        }
    }

    /**
     * Creates a new list nested under this one and hands its id back so the caller
     * can open it. The child starts with the same placeholder title the index uses;
     * it is renamed from the detail screen like any other list.
     */
    fun createChildList(onCreated: (String) -> Unit = {}) {
        val parent = _uiState.value.summary ?: return
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            when (val result = repository.createList(title = NEW_CHILD_TITLE, parentId = parent.id)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isSaving = false) }
                    onCreated(result.data.id)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = result.error.toUserMessage(),
                        subscriptionRequired = result.error.isSubscriptionGate,
                    )
                }
            }
        }
    }

    fun clearRefreshMessage() = _uiState.update { it.copy(refreshMessage = null) }

    /**
     * Renames / re-describes / toggles the visibility of the list. The summary is
     * updated optimistically so the change shows instantly; a failure rolls it back
     * to the previous summary and surfaces the error.
     */
    fun editMetadata(
        title: String,
        description: String?,
        isPublic: Boolean,
        onDone: () -> Unit = {},
    ) {
        val previous = _uiState.value.summary ?: return
        val trimmedTitle = title.trim().ifBlank { previous.title }
        val trimmedDescription = description?.trim()?.ifBlank { null }
        val optimistic = previous.copy(
            title = trimmedTitle,
            description = trimmedDescription,
            isPublic = isPublic,
        )
        // Optimistic: reflect the edit immediately.
        _uiState.update { it.copy(summary = optimistic, isSaving = true) }
        viewModelScope.launch {
            when (val result = repository.updateList(
                id = listId,
                title = trimmedTitle,
                description = trimmedDescription,
                isPublic = isPublic,
            )) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(summary = result.data, isSaving = false, isEditingMetadata = false) }
                    onDone()
                }
                is ApiResult.Failure -> _uiState.update {
                    // Rollback to the pre-edit summary.
                    it.copy(summary = previous, isSaving = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    fun startEditingMetadata() = _uiState.update { it.copy(isEditingMetadata = true) }

    fun stopEditingMetadata() = _uiState.update { it.copy(isEditingMetadata = false) }

    fun deleteList(onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            when (val result = repository.deleteList(listId)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(deleted = true) }
                    onDeleted()
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }

    companion object {
        private const val NEW_CHILD_TITLE = "New list"

        /** Beat interval while rows are actually moving — the first-party grid's own. */
        const val ACTIVE_INTERVAL_MS = 10_000L

        /** Backed-off interval once a beat brings no news, or after a failed beat. */
        const val IDLE_INTERVAL_MS = 60_000L

        /** Polling stops entirely after this long without the user doing anything. */
        const val MAX_IDLE_MS = 10 * 60_000L
    }
}
