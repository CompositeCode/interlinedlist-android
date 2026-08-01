package com.interlinedlist.android.feature.lists.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.lists.data.ListsRepository
import com.interlinedlist.android.feature.lists.domain.ListSummary
import com.interlinedlist.android.feature.lists.ui.isSubscriptionGate
import com.interlinedlist.android.feature.lists.ui.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** UI state for the lists index. */
data class ListsUiState(
    val lists: List<ListSummary> = emptyList(),
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val nextOffset: Int = 0,
    val errorMessage: String? = null,
    val subscriptionRequired: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<ListSummary>? = null,
) {
    /** Rows to render: search results when searching, otherwise the cached index. */
    val visibleLists: List<ListSummary> get() = searchResults ?: lists
    val isSearching: Boolean get() = searchResults != null
    val isEmpty: Boolean
        get() = visibleLists.isEmpty() && !isRefreshing && errorMessage == null && !subscriptionRequired
}

@HiltViewModel
class ListsViewModel @Inject constructor(
    private val repository: ListsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ListsUiState())

    /**
     * Combines the persisted Room stream (source of truth) with transient UI flags
     * so the list stays live as the cache changes while refresh/error state layers
     * on top.
     */
    val uiState: StateFlow<ListsUiState> = combine(
        repository.observeLists(),
        _uiState,
    ) { cached, transient ->
        transient.copy(lists = cached)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = _uiState.value,
    )

    /** Exposed for tests that assert only the transient flags. */
    val transientState: StateFlow<ListsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true, errorMessage = null, subscriptionRequired = false) }
        viewModelScope.launch {
            when (val result = repository.refreshLists()) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        hasMore = result.data.hasMore,
                        nextOffset = result.data.offset,
                    )
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        // Cache still renders via the Room stream; surface the reason.
                        errorMessage = result.error.toUserMessage(),
                        subscriptionRequired = result.error.isSubscriptionGate,
                    )
                }
            }
        }
    }

    fun loadMore() {
        val current = _uiState.value
        if (current.isLoadingMore || !current.hasMore || current.isSearching) return
        _uiState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            when (val result = repository.loadMoreLists(offset = current.nextOffset)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        isLoadingMore = false,
                        hasMore = result.data.hasMore,
                        nextOffset = result.data.offset,
                    )
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isLoadingMore = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    fun createList(title: String, description: String?, onCreated: (ListSummary) -> Unit = {}) {
        if (title.isBlank()) return
        viewModelScope.launch {
            when (val result = repository.createList(title.trim(), description?.trim()?.ifBlank { null }, isPublic = false)) {
                is ApiResult.Success -> onCreated(result.data)
                is ApiResult.Failure -> _uiState.update {
                    it.copy(
                        errorMessage = result.error.toUserMessage(),
                        subscriptionRequired = result.error.isSubscriptionGate,
                    )
                }
            }
        }
    }

    fun deleteList(id: String) {
        viewModelScope.launch {
            when (val result = repository.deleteList(id)) {
                is ApiResult.Success -> Unit // Room stream drops the row.
                is ApiResult.Failure -> _uiState.update { it.copy(errorMessage = result.error.toUserMessage()) }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = null) }
            return
        }
        viewModelScope.launch {
            when (val result = repository.searchLists(query.trim())) {
                is ApiResult.Success -> _uiState.update { it.copy(searchResults = result.data) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(searchResults = emptyList(), errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
}
