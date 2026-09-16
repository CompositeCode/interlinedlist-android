package com.interlinedlist.android.feature.documents.ui.collaborators

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.documents.data.DocumentsRepository
import com.interlinedlist.android.feature.documents.domain.Collaborator
import com.interlinedlist.android.feature.documents.domain.CollaboratorCandidate
import com.interlinedlist.android.feature.documents.domain.CollaboratorRole
import com.interlinedlist.android.feature.documents.domain.DocumentInvite
import com.interlinedlist.android.feature.documents.domain.InviteEmail
import com.interlinedlist.android.feature.documents.domain.InviteRole
import com.interlinedlist.android.feature.documents.ui.common.isSubscriptionGate
import com.interlinedlist.android.feature.documents.ui.common.toInviteMessage
import com.interlinedlist.android.feature.documents.ui.common.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Nav argument key the Manage-access route reads its document id from. */
const val COLLABORATORS_DOCUMENT_ID_ARG = "documentId"

/** UI state for the "Manage access" sheet. */
data class DocumentCollaboratorsUiState(
    val collaborators: List<Collaborator> = emptyList(),
    val candidates: List<CollaboratorCandidate> = emptyList(),
    val searchQuery: String = "",
    val selectedRole: CollaboratorRole = CollaboratorRole.VIEWER,
    val isLoading: Boolean = true,
    val isSearching: Boolean = false,
    val errorMessage: String? = null,
    /** The "Invite by email" section, which sits alongside the collaborator roles. */
    val invites: DocumentInvitesUiState = DocumentInvitesUiState(),
) {
    val isEmpty: Boolean get() = collaborators.isEmpty() && !isLoading && errorMessage == null
}

/**
 * State of the email-invite section: the entry form plus the pending-invite list.
 * Kept as its own type so the section stays self-contained (and so the identical
 * list-invite section can mirror it).
 */
data class DocumentInvitesUiState(
    val invites: List<DocumentInvite> = emptyList(),
    val email: String = "",
    val role: InviteRole = InviteRole.VIEWER,
    val isLoading: Boolean = true,
    val isSending: Boolean = false,
    /** Inline validation message for the address field. */
    val emailError: String? = null,
    val errorMessage: String? = null,
    /** True when sending was refused because the account is not a subscriber. */
    val subscriptionRequired: Boolean = false,
) {
    /** True when the entered address is worth sending — drives the Send control. */
    val canSend: Boolean get() = !isSending && InviteEmail.isValid(email)

    val isEmpty: Boolean get() = invites.isEmpty() && !isLoading
}

/**
 * Drives the Manage-access sheet: list collaborators, search invitable users, invite
 * at a chosen role, change a role, and revoke — all with optimistic UI + rollback so
 * the sheet feels instant and self-corrects on failure.
 */
@HiltViewModel
class DocumentCollaboratorsViewModel @Inject constructor(
    private val repository: DocumentsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val documentId: String = requireNotNull(savedStateHandle[COLLABORATORS_DOCUMENT_ID_ARG]) {
        "DocumentCollaboratorsViewModel requires a '$COLLABORATORS_DOCUMENT_ID_ARG' nav argument"
    }

    private val _uiState = MutableStateFlow(DocumentCollaboratorsUiState())
    val uiState: StateFlow<DocumentCollaboratorsUiState> = _uiState.asStateFlow()

    init {
        load()
        loadInvites()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.getCollaborators(documentId)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(collaborators = result.data, isLoading = false)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    fun onSearchQueryChange(query: String) = _uiState.update { it.copy(searchQuery = query) }

    fun selectRole(role: CollaboratorRole) = _uiState.update { it.copy(selectedRole = role) }

    fun searchUsers() {
        val query = _uiState.value.searchQuery
        _uiState.update { it.copy(isSearching = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.searchCollaboratorUsers(documentId, query)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(candidates = result.data, isSearching = false)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isSearching = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    /** Invites [candidate] at the selected role, optimistically; rolls back on failure. */
    fun invite(candidate: CollaboratorCandidate) {
        val role = _uiState.value.selectedRole
        val previous = _uiState.value.collaborators
        val optimistic = Collaborator(
            userId = candidate.userId,
            role = role,
            displayName = candidate.displayName,
            username = candidate.username,
            email = candidate.email,
            avatarUrl = candidate.avatarUrl,
        )
        _uiState.update {
            it.copy(
                collaborators = it.collaborators.filterNot { c -> c.userId == candidate.userId } + optimistic,
                candidates = it.candidates.filterNot { u -> u.userId == candidate.userId },
            )
        }
        viewModelScope.launch {
            when (val result = repository.inviteCollaborator(documentId, candidate.userId, role)) {
                is ApiResult.Success -> _uiState.update { state ->
                    // Replace the optimistic row with the server's authoritative record.
                    state.copy(
                        collaborators = state.collaborators.map {
                            if (it.userId == candidate.userId) result.data else it
                        },
                    )
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(collaborators = previous, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    /** Optimistically applies a role change; rolls back on failure. */
    fun changeRole(userId: String, role: CollaboratorRole) {
        val previous = _uiState.value.collaborators
        _uiState.update { state ->
            state.copy(
                collaborators = state.collaborators.map {
                    if (it.userId == userId) it.copy(role = role) else it
                },
            )
        }
        viewModelScope.launch {
            when (val result = repository.updateCollaboratorRole(documentId, userId, role)) {
                is ApiResult.Success -> Unit
                is ApiResult.Failure -> _uiState.update {
                    it.copy(collaborators = previous, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    /** Optimistically removes a collaborator; restores them on failure. */
    fun revoke(userId: String) {
        val previous = _uiState.value.collaborators
        _uiState.update { it.copy(collaborators = it.collaborators.filterNot { c -> c.userId == userId }) }
        viewModelScope.launch {
            when (val result = repository.removeCollaborator(documentId, userId)) {
                is ApiResult.Success -> Unit
                is ApiResult.Failure -> _uiState.update {
                    it.copy(collaborators = previous, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }

    // --- Email invites -----------------------------------------------------

    /** Loads the pending invites. Free for any owner, so it is never gated. */
    fun loadInvites() {
        updateInvites { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.getInvites(documentId)) {
                is ApiResult.Success -> updateInvites {
                    it.copy(invites = result.data, isLoading = false)
                }
                is ApiResult.Failure -> updateInvites {
                    it.copy(isLoading = false, errorMessage = result.error.toInviteMessage())
                }
            }
        }
    }

    fun onInviteEmailChange(email: String) =
        updateInvites { it.copy(email = email, emailError = null, errorMessage = null) }

    fun selectInviteRole(role: InviteRole) = updateInvites { it.copy(role = role) }

    /**
     * Sends the invite. An address that is not syntactically valid is rejected here,
     * so no request is made; everything else (ownership, the subscriber gate, an
     * address that cannot be invited) is reported by the server and surfaced as-is.
     */
    fun sendInvite() {
        val form = _uiState.value.invites
        if (form.isSending) return
        if (!InviteEmail.isValid(form.email)) {
            updateInvites { it.copy(emailError = InviteEmail.INVALID_MESSAGE) }
            return
        }
        updateInvites {
            it.copy(isSending = true, emailError = null, errorMessage = null, subscriptionRequired = false)
        }
        viewModelScope.launch {
            when (val result = repository.sendInvite(documentId, form.email, form.role)) {
                is ApiResult.Success -> updateInvites { state ->
                    // Re-inviting an address is idempotent server-side (a fresh token
                    // replaces the old one), so replace any row for the same address.
                    val sent = result.data
                    state.copy(
                        invites = state.invites.filterNot { it.email.equals(sent.email, ignoreCase = true) } + sent,
                        email = "",
                        isSending = false,
                    )
                }
                is ApiResult.Failure -> updateInvites {
                    it.copy(
                        isSending = false,
                        errorMessage = result.error.toInviteMessage(),
                        subscriptionRequired = result.error.isSubscriptionGate,
                    )
                }
            }
        }
    }

    /** Optimistically drops the invite row; restores it if the revoke fails. */
    fun revokeInvite(token: String) {
        val previous = _uiState.value.invites.invites
        updateInvites { state ->
            state.copy(invites = state.invites.filterNot { it.token == token }, errorMessage = null)
        }
        viewModelScope.launch {
            when (val result = repository.revokeInvite(documentId, token)) {
                is ApiResult.Success -> Unit
                is ApiResult.Failure -> updateInvites {
                    it.copy(invites = previous, errorMessage = result.error.toInviteMessage())
                }
            }
        }
    }

    private fun updateInvites(transform: (DocumentInvitesUiState) -> DocumentInvitesUiState) =
        _uiState.update { it.copy(invites = transform(it.invites)) }
}
