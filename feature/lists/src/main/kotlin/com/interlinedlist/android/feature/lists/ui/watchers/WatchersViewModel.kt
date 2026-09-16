package com.interlinedlist.android.feature.lists.ui.watchers

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.lists.data.ListsRepository
import com.interlinedlist.android.feature.lists.domain.Contributor
import com.interlinedlist.android.feature.lists.domain.InviteEmail
import com.interlinedlist.android.feature.lists.domain.InviteRole
import com.interlinedlist.android.feature.lists.domain.ListInvite
import com.interlinedlist.android.feature.lists.domain.Watcher
import com.interlinedlist.android.feature.lists.domain.WatcherCandidate
import com.interlinedlist.android.feature.lists.domain.WatcherRole
import com.interlinedlist.android.feature.lists.ui.isSubscriptionGate
import com.interlinedlist.android.feature.lists.ui.toInviteMessage
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
    /** The "Invite by email" section, which sits alongside the per-person roles. */
    val invites: ListInvitesUiState = ListInvitesUiState(),
) {
    val isEmpty: Boolean get() = watchers.isEmpty() && !isLoading && errorMessage == null
}

/**
 * State of the email-invite section: the entry form plus the pending-invite list.
 * Kept as its own type so the section stays self-contained.
 */
data class ListInvitesUiState(
    val invites: List<ListInvite> = emptyList(),
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
        loadInvites()
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

    // --- Email invites -----------------------------------------------------

    /** Loads the pending invites. Free for any owner, so it is never gated. */
    fun loadInvites() {
        updateInvites { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.getInvites(listId)) {
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
            when (val result = repository.sendInvite(listId, form.email, form.role)) {
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
            when (val result = repository.revokeInvite(listId, token)) {
                is ApiResult.Success -> Unit
                is ApiResult.Failure -> updateInvites {
                    it.copy(invites = previous, errorMessage = result.error.toInviteMessage())
                }
            }
        }
    }

    private fun updateInvites(transform: (ListInvitesUiState) -> ListInvitesUiState) =
        _uiState.update { it.copy(invites = transform(it.invites)) }
}
