package com.interlinedlist.android.feature.profile.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.profile.data.ProfileRepository
import com.interlinedlist.android.feature.profile.domain.UserSearchResult
import com.interlinedlist.android.feature.profile.ui.common.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** UI state for the user-search screen. */
data class UserSearchUiState(
    val query: String = "",
    val results: List<UserSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val errorMessage: String? = null,
) {
    /** A query was run and came back empty (so the UI can say "no matches"). */
    val isEmptyResult: Boolean
        get() = query.trim().length >= MIN_QUERY_LENGTH && results.isEmpty() && !isSearching && errorMessage == null

    companion object {
        const val MIN_QUERY_LENGTH = 2
    }
}

/**
 * Backs user search. Debounces the query, runs `GET /api/users/search`, and exposes
 * results the UI drills into (open a profile by username).
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class UserSearchViewModel @Inject constructor(
    private val repository: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserSearchUiState())
    val uiState: StateFlow<UserSearchUiState> = _uiState.asStateFlow()

    private val queryFlow = MutableStateFlow("")

    init {
        observeQuery()
    }

    private fun observeQuery() {
        viewModelScope.launch {
            queryFlow
                .debounce(DEBOUNCE_MILLIS)
                .distinctUntilChanged()
                .collect { query -> runSearch(query) }
        }
    }

    fun onQueryChange(value: String) {
        _uiState.update { it.copy(query = value, errorMessage = null) }
        if (value.trim().length < UserSearchUiState.MIN_QUERY_LENGTH) {
            _uiState.update { it.copy(results = emptyList(), isSearching = false) }
        }
        queryFlow.value = value.trim()
    }

    private suspend fun runSearch(query: String) {
        if (query.length < UserSearchUiState.MIN_QUERY_LENGTH) {
            _uiState.update { it.copy(results = emptyList(), isSearching = false) }
            return
        }
        _uiState.update { it.copy(isSearching = true, errorMessage = null) }
        when (val result = repository.searchUsers(query)) {
            is ApiResult.Success -> _uiState.update {
                it.copy(isSearching = false, results = result.data)
            }
            is ApiResult.Failure -> _uiState.update {
                it.copy(isSearching = false, results = emptyList(), errorMessage = result.error.toUserMessage())
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }

    private companion object {
        const val DEBOUNCE_MILLIS = 300L
    }
}
