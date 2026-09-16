package com.interlinedlist.android.feature.lists.ui.views

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.lists.data.CurrentUserIdProvider
import com.interlinedlist.android.feature.lists.data.ListsRepository
import com.interlinedlist.android.feature.lists.domain.ListView
import com.interlinedlist.android.feature.lists.domain.ListViewScope
import com.interlinedlist.android.feature.lists.ui.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The nav argument key the views switcher reads its list id from. */
const val VIEWS_LIST_ID_ARG = "listId"

/** UI state for the saved-view switcher on the list detail screen. */
data class ListViewsUiState(
    val views: List<ListView> = emptyList(),
    val selectedViewId: String? = null,
    val currentUserId: String? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isSwitcherOpen: Boolean = false,
    val errorMessage: String? = null,
) {
    val selectedView: ListView? get() = views.firstOrNull { it.id == selectedViewId }
    val sharedViews: List<ListView> get() = views.filter { it.isShared }
    val personalViews: List<ListView> get() = views.filterNot { it.isShared }
    val isEmpty: Boolean get() = views.isEmpty() && !isLoading

    /** Only the view's owner may rename or delete it; everyone else forks a copy. */
    fun canModify(view: ListView): Boolean = view.isOwnedBy(currentUserId)

    /** Forking is offered on shared views — the escape hatch when one doesn't suit. */
    fun canFork(view: ListView): Boolean = view.isShared
}

/**
 * Drives the saved-view switcher: loads the list's shared views plus the user's
 * own personal ones, tracks which is selected (the default wins on first load),
 * and creates / renames / deletes / re-defaults / forks them.
 *
 * Two rules shape every write:
 * - the server silently drops `config` values it does not recognise, so the view
 *   it returns replaces the local one rather than the optimistic copy we sent;
 * - renaming and deleting are offered only for views the user owns, and a server
 *   refusal is surfaced as a message with the view left untouched.
 */
@HiltViewModel
class ListViewsViewModel @Inject constructor(
    private val repository: ListsRepository,
    private val currentUserIdProvider: CurrentUserIdProvider,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val listId: String = requireNotNull(savedStateHandle[VIEWS_LIST_ID_ARG]) {
        "ListViewsViewModel requires a '$VIEWS_LIST_ID_ARG' nav argument"
    }

    private val _uiState = MutableStateFlow(ListViewsUiState(currentUserId = currentUserIdProvider.currentUserId()))
    val uiState: StateFlow<ListViewsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch { refresh(showLoading = true) }
    }

    fun openSwitcher() = _uiState.update { it.copy(isSwitcherOpen = true) }

    fun closeSwitcher() = _uiState.update { it.copy(isSwitcherOpen = false) }

    fun selectView(viewId: String) = _uiState.update { it.copy(selectedViewId = viewId) }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }

    /**
     * Creates a view with the chosen [scope]. A missing scope never reaches the
     * network — the API rejects it — and comes back as a message instead.
     */
    fun createView(name: String, scope: ListViewScope?, onDone: () -> Unit = {}) {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.createView(listId, name, scope)) {
                is ApiResult.Success -> {
                    // The server's copy is authoritative — it may have dropped config values.
                    _uiState.update {
                        it.copy(
                            views = it.views + result.data,
                            selectedViewId = result.data.id,
                            isSaving = false,
                        )
                    }
                    onDone()
                }
                is ApiResult.Failure -> fail(result)
            }
        }
    }

    /** Renames a view the user owns; somebody else's shared view is refused locally. */
    fun renameView(view: ListView, name: String) {
        if (!requireOwnership(view, RENAME_REFUSED)) return
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.updateView(listId, view.id, name = name)) {
                is ApiResult.Success -> applyServerView(result.data)
                is ApiResult.Failure -> fail(result)
            }
        }
    }

    /**
     * Makes a view the default. Whether the server lets a user re-default somebody
     * else's shared view is its call, so this is attempted and any refusal shown;
     * the list is re-read afterwards so sibling views lose the flag if it moved.
     */
    fun setDefault(view: ListView) {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.updateView(listId, view.id, isDefault = true)) {
                is ApiResult.Success -> {
                    applyServerView(result.data)
                    refresh(showLoading = false)
                }
                is ApiResult.Failure -> fail(result)
            }
        }
    }

    /**
     * Forks a shared view into a personal copy and selects it. The original is
     * untouched, which is the whole point: it is the way out when somebody else's
     * shared view does not suit.
     */
    fun forkView(view: ListView) {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.forkView(listId, view.id)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        views = it.views + result.data,
                        selectedViewId = result.data.id,
                        isSaving = false,
                    )
                }
                is ApiResult.Failure -> fail(result)
            }
        }
    }

    /** Deletes a view the user owns; somebody else's shared view is refused locally. */
    fun deleteView(view: ListView) {
        if (!requireOwnership(view, DELETE_REFUSED)) return
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.deleteView(listId, view.id)) {
                is ApiResult.Success -> _uiState.update { state ->
                    // Removed only once the server confirms, so a refusal leaves it visible.
                    val remaining = state.views.filterNot { it.id == view.id }
                    state.copy(
                        views = remaining,
                        selectedViewId = state.selectedViewId
                            .takeIf { it != view.id } ?: remaining.preferredSelection(),
                        isSaving = false,
                    )
                }
                is ApiResult.Failure -> fail(result)
            }
        }
    }

    /** Re-reads the views, keeping the current selection when it still exists. */
    private suspend fun refresh(showLoading: Boolean) {
        when (val result = repository.getViews(listId)) {
            is ApiResult.Success -> _uiState.update { state ->
                val views = result.data
                state.copy(
                    views = views,
                    selectedViewId = state.selectedViewId?.takeIf { id -> views.any { it.id == id } }
                        ?: views.preferredSelection(),
                    currentUserId = currentUserIdProvider.currentUserId(),
                    isLoading = false,
                    isSaving = false,
                )
            }
            is ApiResult.Failure -> _uiState.update {
                it.copy(
                    isLoading = false,
                    isSaving = false,
                    // A failed background reconcile shouldn't bury the write's own error.
                    errorMessage = if (showLoading) result.error.toUserMessage() else it.errorMessage,
                )
            }
        }
    }

    /** Replaces a view with the server's copy of it, which is the authoritative one. */
    private fun applyServerView(view: ListView) = _uiState.update { state ->
        state.copy(
            views = state.views.map { if (it.id == view.id) view else it },
            isSaving = false,
        )
    }

    private fun fail(result: ApiResult.Failure) = _uiState.update {
        it.copy(isSaving = false, errorMessage = result.error.toUserMessage())
    }

    /** Refuses an action on a view the user does not own, without spending a request. */
    private fun requireOwnership(view: ListView, message: String): Boolean {
        if (_uiState.value.canModify(view)) return true
        _uiState.update { it.copy(errorMessage = message) }
        return false
    }

    private companion object {
        const val RENAME_REFUSED = "Only the person who created this shared view can rename it. Make a personal copy instead."
        const val DELETE_REFUSED = "Only the person who created this shared view can delete it."
    }
}

/** The default view if there is one, else the first — what a fresh screen shows. */
private fun List<ListView>.preferredSelection(): String? =
    (firstOrNull { it.isDefault } ?: firstOrNull())?.id
