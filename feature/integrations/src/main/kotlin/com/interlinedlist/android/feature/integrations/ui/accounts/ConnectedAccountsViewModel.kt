package com.interlinedlist.android.feature.integrations.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.integrations.data.IntegrationsRepository
import com.interlinedlist.android.feature.integrations.domain.ConnectedAccount
import com.interlinedlist.android.feature.integrations.ui.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** UI state for the "Connected accounts" screen. */
data class ConnectedAccountsUiState(
    val isLoading: Boolean = true,
    val accounts: List<ConnectedAccount> = emptyList(),
    /** Keys of rows with an unlink or verify request in flight. */
    val pendingKeys: Set<String> = emptySet(),
    /** The account whose unlink confirmation is on screen, if any. */
    val unlinkCandidate: ConnectedAccount? = null,
    /** Transient success text for the snackbar. */
    val message: String? = null,
    /** Transient failure text for the snackbar — the server's message where it gave one. */
    val errorMessage: String? = null,
)

/**
 * Drives the "Connected accounts" screen.
 *
 * Linking still happens on the web, but unlinking and re-verifying an already-linked
 * identity are ordinary API calls, so both are offered here. Neither is applied
 * optimistically: the list is re-read from the server after a successful call so the
 * screen can never show a state that is no longer true, and a failed unlink leaves the
 * connection exactly where it was with the server's own message surfaced.
 */
@HiltViewModel
class ConnectedAccountsViewModel @Inject constructor(
    private val repository: IntegrationsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectedAccountsUiState())
    val uiState: StateFlow<ConnectedAccountsUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch { reload() }
    }

    /** Opens the unlink confirmation; unlinking never happens straight off a tap. */
    fun requestUnlink(account: ConnectedAccount) {
        if (!account.isLinked) return
        _uiState.update { it.copy(unlinkCandidate = account) }
    }

    fun dismissUnlinkRequest() = _uiState.update { it.copy(unlinkCandidate = null) }

    /** Performs the unlink the user just confirmed, then re-reads the list. */
    fun confirmUnlink() {
        val account = _uiState.value.unlinkCandidate ?: return
        _uiState.update { it.copy(unlinkCandidate = null) }
        mutate(account) { provider -> repository.unlinkIdentity(provider) to account.unlinkedMessage() }
    }

    /** Re-verifies a linked connection, then re-reads the list so the health badge updates. */
    fun verify(account: ConnectedAccount) {
        mutate(account) { provider -> repository.verifyIdentity(provider) to account.verifiedMessage() }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }

    /**
     * Shared shape of both mutations: mark the row busy, call, and on success announce
     * it and re-read the list; on failure surface the server's message and change
     * nothing else. In-flight taps on the same row are deduped.
     */
    private fun mutate(
        account: ConnectedAccount,
        action: suspend (identityProvider: String) -> Pair<ApiResult<Unit>, String>,
    ) {
        val identityProvider = account.identityProvider ?: return
        if (account.key in _uiState.value.pendingKeys) return

        _uiState.update {
            it.copy(pendingKeys = it.pendingKeys + account.key, errorMessage = null, message = null)
        }
        viewModelScope.launch {
            val (result, successMessage) = action(identityProvider)
            when (result) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(pendingKeys = it.pendingKeys - account.key, message = successMessage)
                    }
                    reload()
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(
                        pendingKeys = it.pendingKeys - account.key,
                        errorMessage = result.error.toUserMessage(),
                    )
                }
            }
        }
    }

    private suspend fun reload() {
        val accounts = repository.getConnectedAccounts()
        _uiState.update { it.copy(isLoading = false, accounts = accounts) }
    }
}
