package com.interlinedlist.android.feature.organizations.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.organizations.data.OrganizationsRepository
import com.interlinedlist.android.feature.organizations.domain.Organization
import com.interlinedlist.android.feature.organizations.ui.isSubscriptionGate
import com.interlinedlist.android.feature.organizations.ui.toUserMessage
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

/** UI state for the organizations index. */
data class OrganizationsUiState(
    val organizations: List<Organization> = emptyList(),
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val nextOffset: Int = 0,
    val errorMessage: String? = null,
    val subscriptionRequired: Boolean = false,
) {
    val isEmpty: Boolean
        get() = organizations.isEmpty() && !isRefreshing && errorMessage == null && !subscriptionRequired
}

@HiltViewModel
class OrganizationsViewModel @Inject constructor(
    private val repository: OrganizationsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrganizationsUiState())

    /**
     * Combines the persisted Room stream (source of truth) with transient UI flags
     * so the index stays live as the cache changes while refresh/error state layers
     * on top.
     */
    val uiState: StateFlow<OrganizationsUiState> = combine(
        repository.observeOrganizations(),
        _uiState,
    ) { cached, transient ->
        transient.copy(organizations = cached)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = _uiState.value,
    )

    /** Exposed for tests that assert only the transient flags. */
    val transientState: StateFlow<OrganizationsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true, errorMessage = null, subscriptionRequired = false) }
        viewModelScope.launch {
            when (val result = repository.refreshOrganizations()) {
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
        if (current.isLoadingMore || !current.hasMore) return
        _uiState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            when (val result = repository.loadMoreOrganizations(offset = current.nextOffset)) {
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

    fun createOrganization(
        name: String,
        description: String?,
        isPublic: Boolean = false,
        onCreated: (Organization) -> Unit = {},
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val result = repository.createOrganization(
                name = name.trim(),
                description = description?.trim()?.ifBlank { null },
                isPublic = isPublic,
            )
            when (result) {
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

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
}
