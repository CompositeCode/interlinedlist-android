package com.interlinedlist.android.feature.organizations.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.organizations.data.OrganizationsRepository
import com.interlinedlist.android.feature.organizations.domain.MemberCandidate
import com.interlinedlist.android.feature.organizations.domain.OrgMember
import com.interlinedlist.android.feature.organizations.domain.OrgRole
import com.interlinedlist.android.feature.organizations.domain.Organization
import com.interlinedlist.android.feature.organizations.ui.isSubscriptionGate
import com.interlinedlist.android.feature.organizations.ui.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The nav argument key the detail route reads its org id from. */
const val ORG_ID_ARG = "orgId"

/** UI state for the organization detail screen (metadata + members management). */
data class OrganizationDetailUiState(
    val organization: Organization? = null,
    val members: List<OrgMember> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val subscriptionRequired: Boolean = false,
    val deleted: Boolean = false,
    // Member search / add.
    val searchQuery: String = "",
    val candidates: List<MemberCandidate> = emptyList(),
    val isSearching: Boolean = false,
) {
    val title: String get() = organization?.displayName.orEmpty()
    val isEmpty: Boolean get() = members.isEmpty() && !isLoading && errorMessage == null
}

@HiltViewModel
class OrganizationDetailViewModel @Inject constructor(
    private val repository: OrganizationsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val orgId: String = requireNotNull(savedStateHandle[ORG_ID_ARG]) {
        "OrganizationDetailViewModel requires an '$ORG_ID_ARG' nav argument"
    }

    private val _uiState = MutableStateFlow(OrganizationDetailUiState())
    val uiState: StateFlow<OrganizationDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null, subscriptionRequired = false) }
        viewModelScope.launch {
            when (val result = repository.getOrganization(orgId)) {
                is ApiResult.Success -> _uiState.update { it.copy(organization = result.data, isLoading = false) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = result.error.toUserMessage(),
                        subscriptionRequired = result.error.isSubscriptionGate,
                    )
                }
            }
            // Members are loaded after metadata; a failure surfaces but keeps the header.
            when (val members = repository.getMembers(orgId)) {
                is ApiResult.Success -> _uiState.update { it.copy(members = members.data) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(errorMessage = it.errorMessage ?: members.error.toUserMessage())
                }
            }
        }
    }

    fun updateOrganization(
        name: String?,
        description: String?,
        isPublic: Boolean?,
        onDone: () -> Unit = {},
    ) {
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            when (val result = repository.updateOrganization(orgId, name, description, isPublic)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isSaving = false, organization = result.data) }
                    onDone()
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isSaving = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    fun deleteOrganization(onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            when (val result = repository.deleteOrganization(orgId)) {
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

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        if (query.isBlank()) {
            _uiState.update { it.copy(candidates = emptyList(), isSearching = false) }
            return
        }
        _uiState.update { it.copy(isSearching = true) }
        viewModelScope.launch {
            when (val result = repository.searchMemberCandidates(orgId, query.trim())) {
                is ApiResult.Success -> _uiState.update { it.copy(candidates = result.data, isSearching = false) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(candidates = emptyList(), isSearching = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    fun addMember(candidate: MemberCandidate, role: OrgRole = OrgRole.MEMBER) {
        viewModelScope.launch {
            when (val result = repository.addMember(orgId, candidate.userId, role)) {
                is ApiResult.Success -> {
                    // Clear the search and reload so the new member's server-side role shows.
                    _uiState.update { it.copy(searchQuery = "", candidates = emptyList()) }
                    reloadMembers()
                }
                is ApiResult.Failure -> _uiState.update { it.copy(errorMessage = result.error.toUserMessage()) }
            }
        }
    }

    fun changeRole(member: OrgMember, role: OrgRole) {
        if (member.role == role) return
        viewModelScope.launch {
            when (val result = repository.updateMemberRole(orgId, member.userId, role)) {
                is ApiResult.Success -> _uiState.update { state ->
                    state.copy(
                        members = state.members.map {
                            if (it.userId == member.userId) it.copy(role = role) else it
                        },
                    )
                }
                is ApiResult.Failure -> _uiState.update { it.copy(errorMessage = result.error.toUserMessage()) }
            }
        }
    }

    fun removeMember(member: OrgMember) {
        viewModelScope.launch {
            when (val result = repository.removeMember(orgId, member.userId)) {
                is ApiResult.Success -> _uiState.update { state ->
                    state.copy(members = state.members.filterNot { it.userId == member.userId })
                }
                is ApiResult.Failure -> _uiState.update { it.copy(errorMessage = result.error.toUserMessage()) }
            }
        }
    }

    /** Reloads only the member list without toggling the header loading spinner. */
    private fun reloadMembers() {
        viewModelScope.launch {
            when (val members = repository.getMembers(orgId)) {
                is ApiResult.Success -> _uiState.update { it.copy(members = members.data) }
                is ApiResult.Failure -> Unit // Keep the existing list; the add already succeeded.
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
}
