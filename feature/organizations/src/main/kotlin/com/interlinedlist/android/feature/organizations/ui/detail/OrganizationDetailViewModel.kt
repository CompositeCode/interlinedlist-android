package com.interlinedlist.android.feature.organizations.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.organizations.data.OrganizationsRepository
import com.interlinedlist.android.feature.organizations.domain.MemberCandidate
import com.interlinedlist.android.feature.organizations.domain.OrgLinkedInStatus
import com.interlinedlist.android.feature.organizations.domain.OrgMember
import com.interlinedlist.android.feature.organizations.domain.OrgPermissions
import com.interlinedlist.android.feature.organizations.domain.OrgRole
import com.interlinedlist.android.feature.organizations.domain.Organization
import com.interlinedlist.android.feature.organizations.ui.LAST_OWNER_DEMOTE_EXPLANATION
import com.interlinedlist.android.feature.organizations.ui.LAST_OWNER_EXPLANATION
import com.interlinedlist.android.feature.organizations.ui.LAST_OWNER_REMOVE_EXPLANATION
import com.interlinedlist.android.feature.organizations.ui.isMissingLinkedInCredential
import com.interlinedlist.android.feature.organizations.ui.isSubscriptionGate
import com.interlinedlist.android.feature.organizations.ui.toLinkedInMessage
import com.interlinedlist.android.feature.organizations.ui.toJoinMessage
import com.interlinedlist.android.feature.organizations.ui.toLeaveMessage
import com.interlinedlist.android.feature.organizations.ui.toRemoveMemberMessage
import com.interlinedlist.android.feature.organizations.ui.toRoleChangeMessage
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
    // Membership (join / leave) in flight.
    val isJoining: Boolean = false,
    val isLeaving: Boolean = false,
    // Member search / add.
    val searchQuery: String = "",
    val candidates: List<MemberCandidate> = emptyList(),
    val isSearching: Boolean = false,
    // Shared LinkedIn credential; null until it has been read (or when the
    // viewer's role may not manage it, in which case it is never requested).
    val linkedIn: OrgLinkedInStatus? = null,
    val isLinkedInLoading: Boolean = false,
    val isLinkedInSyncing: Boolean = false,
    val linkedInError: String? = null,
) {
    val title: String get() = organization?.displayName.orEmpty()
    val isEmpty: Boolean get() = members.isEmpty() && !isLoading && errorMessage == null && isMember

    /**
     * What the signed-in user's role permits here. Drives which affordances render;
     * the server remains authoritative for every mutation.
     */
    val permissions: OrgPermissions get() = OrgPermissions.of(organization)

    /** Whether the signed-in user belongs to this organization. */
    val isMember: Boolean get() = permissions.isMember

    /** A public organization the user has not joined can be joined from here. */
    val canJoin: Boolean get() = permissions.canJoin

    /**
     * Whether the LinkedIn section is offered at all. Only an owner or admin may
     * manage the shared credential, and the server enforces it, so no one else is
     * shown controls that would only be refused.
     */
    val showLinkedIn: Boolean get() = permissions.canManageLinkedIn && organization != null

    /** How many owners the loaded member list holds; the server protects the last one. */
    private val ownerCount: Int get() = members.count { it.role == OrgRole.OWNER }

    /**
     * True when the user is this organization's only owner. Leaving would orphan
     * the organization, and the server refuses it (400 "Cannot remove the last
     * owner"), so the UI explains it up front instead of failing. Requires a loaded
     * member list; without one the server's rejection is the backstop.
     */
    val isLastOwner: Boolean get() = organization?.role == OrgRole.OWNER && ownerCount == 1

    /**
     * True when [member] is the organization's only owner, so demoting or removing
     * them is refused by the server ("Cannot demote/remove the last owner").
     */
    fun isOnlyOwner(member: OrgMember): Boolean =
        member.role == OrgRole.OWNER && ownerCount == 1

    /** Whether [member]'s role may be changed *and* the change would be accepted. */
    fun canChangeRoleOf(member: OrgMember): Boolean = permissions.canChangeRoleOf(member.role)

    /** Whether [member] may be removed by the signed-in user. */
    fun canRemove(member: OrgMember): Boolean = permissions.canRemove(member.role)

    /**
     * Roles offered for [member]. The only owner may not be demoted, so they are
     * offered `owner` alone rather than chips that would be rejected.
     */
    fun assignableRolesFor(member: OrgMember): List<OrgRole> {
        val assignable = permissions.assignableRolesFor(member.role)
        return if (isOnlyOwner(member)) assignable.filter { it == OrgRole.OWNER } else assignable
    }
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
            val organization = when (val result = repository.getOrganization(orgId)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(organization = result.data, isLoading = false) }
                    result.data
                }
                is ApiResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.error.toUserMessage(),
                            subscriptionRequired = result.error.isSubscriptionGate,
                        )
                    }
                    null
                }
            }
            // Members are members-only on the server (403 otherwise), so a
            // non-member sees the join prompt rather than a permission error.
            if (!OrgPermissions.of(organization).canViewMembers) {
                _uiState.update { it.copy(members = emptyList()) }
                return@launch
            }
            when (val members = repository.getMembers(orgId)) {
                is ApiResult.Success -> _uiState.update { it.copy(members = members.data) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(errorMessage = it.errorMessage ?: members.error.toUserMessage())
                }
            }
            // Only an owner or admin manages the shared LinkedIn credential, so
            // nobody else's screen even asks for it.
            if (OrgPermissions.of(organization).canManageLinkedIn) loadLinkedIn()
        }
    }

    /** Joins this (public) organization, then reloads so membership state is server-truth. */
    fun join() {
        if (_uiState.value.isJoining || !_uiState.value.canJoin) return
        _uiState.update { it.copy(isJoining = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.joinOrganization(orgId)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isJoining = false) }
                    load()
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isJoining = false, errorMessage = result.error.toJoinMessage())
                }
            }
        }
    }

    /**
     * Leaves this organization. A sole owner is stopped with an explanation rather
     * than a failed request; if the server refuses anyway (the member list may not
     * have loaded) that rejection is explained the same way.
     */
    fun leave(onLeft: () -> Unit = {}) {
        val state = _uiState.value
        if (state.isLeaving || !state.permissions.canLeave) return
        if (state.isLastOwner) {
            _uiState.update { it.copy(errorMessage = LAST_OWNER_EXPLANATION) }
            return
        }
        _uiState.update { it.copy(isLeaving = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.leaveOrganization(orgId)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isLeaving = false) }
                    onLeft()
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isLeaving = false, errorMessage = result.error.toLeaveMessage())
                }
            }
        }
    }

    /**
     * Saves name, description and visibility. Visibility is sent on the same `PUT`,
     * so it is gated by the same permission.
     */
    fun updateOrganization(
        name: String?,
        description: String?,
        isPublic: Boolean?,
        onDone: () -> Unit = {},
    ) {
        if (!_uiState.value.permissions.canEditOrganization) return
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
        if (!_uiState.value.permissions.canDeleteOrganization) return
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
        if (!_uiState.value.permissions.canAddMember) return
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

    /**
     * Changes a member's role. Demoting the organization's only owner is refused by
     * the server (400 "Cannot demote the last owner"), so it is explained up front;
     * a server rejection is explained the same way when the guard cannot see it.
     */
    fun changeRole(member: OrgMember, role: OrgRole) {
        if (member.role == role) return
        val state = _uiState.value
        if (!state.canChangeRoleOf(member)) return
        if (role != OrgRole.OWNER && state.isOnlyOwner(member)) {
            _uiState.update { it.copy(errorMessage = LAST_OWNER_DEMOTE_EXPLANATION) }
            return
        }
        viewModelScope.launch {
            when (val result = repository.updateMemberRole(orgId, member.userId, role)) {
                is ApiResult.Success -> _uiState.update { current ->
                    current.copy(
                        members = current.members.map {
                            if (it.userId == member.userId) it.copy(role = role) else it
                        },
                    )
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(errorMessage = result.error.toRoleChangeMessage())
                }
            }
        }
    }

    /** Removes a member. The only owner is protected exactly as [changeRole] is. */
    fun removeMember(member: OrgMember) {
        val state = _uiState.value
        if (!state.canRemove(member)) return
        if (state.isOnlyOwner(member)) {
            _uiState.update { it.copy(errorMessage = LAST_OWNER_REMOVE_EXPLANATION) }
            return
        }
        viewModelScope.launch {
            when (val result = repository.removeMember(orgId, member.userId)) {
                is ApiResult.Success -> _uiState.update { current ->
                    current.copy(members = current.members.filterNot { it.userId == member.userId })
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(errorMessage = result.error.toRemoveMemberMessage())
                }
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

    // ---- LinkedIn company pages --------------------------------------------

    /**
     * Reads the shared credential, its pages and the per-member assignments. An
     * organization with no credential is a normal success
     * (`{"credential":null}`), so it lands in [OrgLinkedInStatus.NOT_CONNECTED]
     * and the section says so instead of showing an error.
     */
    fun loadLinkedIn() {
        if (!_uiState.value.permissions.canManageLinkedIn) return
        _uiState.update { it.copy(isLinkedInLoading = true, linkedInError = null) }
        viewModelScope.launch {
            when (val result = repository.getLinkedInStatus(orgId)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(linkedIn = result.data, isLinkedInLoading = false)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isLinkedInLoading = false).withLinkedInFailure(result.error)
                }
            }
        }
    }

    /**
     * Assigns [member] to the company page [pageId], or clears their assignment
     * when it is null. The server takes one pair per call and answers with
     * whether the member ends up assigned, which is what the UI then shows.
     */
    fun assignLinkedInPage(member: OrgMember, pageId: String?) {
        val state = _uiState.value
        if (!state.permissions.canManageLinkedIn) return
        if (state.linkedIn?.connected != true) return
        _uiState.update { it.copy(linkedInError = null) }
        viewModelScope.launch {
            when (val result = repository.assignLinkedInPage(orgId, member.userId, pageId)) {
                is ApiResult.Success -> _uiState.update { current ->
                    val status = current.linkedIn ?: return@update current
                    val assignments = status.assignments.toMutableMap()
                    if (result.data && pageId != null) {
                        assignments[member.userId] = pageId
                    } else {
                        assignments.remove(member.userId)
                    }
                    current.copy(linkedIn = status.copy(assignments = assignments))
                }
                is ApiResult.Failure -> _uiState.update { it.withLinkedInFailure(result.error) }
            }
        }
    }

    /** Re-discovers the organization's company pages and refreshes the list. */
    fun syncLinkedInPages() {
        val state = _uiState.value
        if (!state.permissions.canManageLinkedIn || state.isLinkedInSyncing) return
        _uiState.update { it.copy(isLinkedInSyncing = true, linkedInError = null) }
        viewModelScope.launch {
            when (val result = repository.syncLinkedInPages(orgId)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isLinkedInSyncing = false, linkedIn = result.data)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isLinkedInSyncing = false).withLinkedInFailure(result.error)
                }
            }
        }
    }

    /**
     * Disconnects the shared credential. Destructive — the organization can no
     * longer post to its company pages and every assignment is cleared — so the
     * UI only calls this from a confirmed dialog.
     */
    fun removeLinkedInCredential() {
        if (!_uiState.value.permissions.canManageLinkedIn) return
        _uiState.update { it.copy(linkedInError = null) }
        viewModelScope.launch {
            when (val result = repository.removeLinkedInCredential(orgId)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(linkedIn = OrgLinkedInStatus.NOT_CONNECTED)
                }
                // "No credential" is the outcome the user asked for, not a failure.
                is ApiResult.Failure -> _uiState.update { it.withLinkedInFailure(result.error) }
            }
        }
    }

    /**
     * Applies a LinkedIn failure. An organization whose credential is missing (or
     * has just been disconnected elsewhere) is not an error state: the section
     * becomes the not-connected one. Everything else is explained to the user.
     */
    private fun OrganizationDetailUiState.withLinkedInFailure(error: AppError) =
        if (error.isMissingLinkedInCredential) {
            copy(linkedIn = OrgLinkedInStatus.NOT_CONNECTED, linkedInError = null)
        } else {
            copy(linkedInError = error.toLinkedInMessage())
        }

    fun clearLinkedInError() = _uiState.update { it.copy(linkedInError = null) }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
}
