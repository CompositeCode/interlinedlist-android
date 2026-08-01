package com.interlinedlist.android.feature.lists.ui.watchers

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.lists.data.ListsRepository
import com.interlinedlist.android.feature.lists.domain.Contributor
import com.interlinedlist.android.feature.lists.domain.Watcher
import com.interlinedlist.android.feature.lists.domain.WatcherCandidate
import com.interlinedlist.android.feature.lists.domain.WatcherRole
import com.interlinedlist.android.feature.lists.ui.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The nav argument key the watchers route reads its list id from. */
const val WATCHERS_LIST_ID_ARG = "listId"

/** UI state for the watchers screen. */
data class WatchersUiState(
    val watchers: List<Watcher> = emptyList(),
    val contributors: List<Contributor> = emptyList(),
    val isWatching: Boolean = false,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val searchQuery: String = "",
    val candidates: List<WatcherCandidate> = emptyList(),
    val isSearching: Boolean = false,
) {
    val isEmpty: Boolean get() = watchers.isEmpty() && !isLoading && errorMessage == null
}

@HiltViewModel
class WatchersViewModel @Inject constructor(
    private val repository: ListsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val listId: String = requireNotNull(savedStateHandle[WATCHERS_LIST_ID_ARG]) {
        "WatchersViewModel requires a '$WATCHERS_LIST_ID_ARG' nav argument"
    }

    private val _uiState = MutableStateFlow(WatchersUiState())
    val uiState: StateFlow<WatchersUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.getWatchers(listId)) {
                is ApiResult.Success -> _uiState.update { it.copy(watchers = result.data, isLoading = false) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.error.toUserMessage())
                }
            }
            // The "am I watching?" flag is best-effort; a failure just leaves it false.
            when (val status = repository.isWatching(listId)) {
                is ApiResult.Success -> _uiState.update { it.copy(isWatching = status.data) }
                is ApiResult.Failure -> Unit
            }
            // Contributors are read-only supplementary detail; a failure leaves them empty.
            when (val contributors = repository.getContributors(listId)) {
                is ApiResult.Success -> _uiState.update { it.copy(contributors = contributors.data) }
                is ApiResult.Failure -> Unit
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        if (query.isBlank()) {
            _uiState.update { it.copy(candidates = emptyList(), isSearching = false) }
            return
        }
        _uiState.update { it.copy(isSearching = true) }
        viewModelScope.launch {
            when (val result = repository.searchWatcherCandidates(listId, query.trim())) {
                is ApiResult.Success -> _uiState.update { it.copy(candidates = result.data, isSearching = false) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(candidates = emptyList(), isSearching = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    fun addWatcher(candidate: WatcherCandidate, role: WatcherRole = WatcherRole.VIEWER) {
        viewModelScope.launch {
            when (val result = repository.addWatcher(listId, candidate.userId, role)) {
                is ApiResult.Success -> {
                    // Clear the search and reload so the new watcher's server-side role shows.
                    _uiState.update { it.copy(searchQuery = "", candidates = emptyList()) }
                    load()
                }
                is ApiResult.Failure -> _uiState.update { it.copy(errorMessage = result.error.toUserMessage()) }
            }
        }
    }

    fun changeRole(watcher: Watcher, role: WatcherRole) {
        if (watcher.role == role) return
        viewModelScope.launch {
            when (val result = repository.updateWatcherRole(listId, watcher.userId, role)) {
                is ApiResult.Success -> _uiState.update { state ->
                    state.copy(
                        watchers = state.watchers.map {
                            if (it.userId == watcher.userId) it.copy(role = role) else it
                        },
                    )
                }
                is ApiResult.Failure -> _uiState.update { it.copy(errorMessage = result.error.toUserMessage()) }
            }
        }
    }

    fun removeWatcher(watcher: Watcher) {
        viewModelScope.launch {
            when (val result = repository.removeWatcher(listId, watcher.userId)) {
                is ApiResult.Success -> _uiState.update { state ->
                    state.copy(watchers = state.watchers.filterNot { it.userId == watcher.userId })
                }
                is ApiResult.Failure -> _uiState.update { it.copy(errorMessage = result.error.toUserMessage()) }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
}
