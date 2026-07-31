package com.interlinedlist.android.feature.documents.ui.collaborators

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.documents.data.DocumentsRepository
import com.interlinedlist.android.feature.documents.domain.Collaborator
import com.interlinedlist.android.feature.documents.domain.CollaboratorCandidate
import com.interlinedlist.android.feature.documents.domain.CollaboratorRole
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
) {
    val isEmpty: Boolean get() = collaborators.isEmpty() && !isLoading && errorMessage == null
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
}
