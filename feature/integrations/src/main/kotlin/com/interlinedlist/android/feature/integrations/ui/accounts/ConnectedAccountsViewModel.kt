package com.interlinedlist.android.feature.integrations.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.feature.integrations.data.IntegrationsRepository
import com.interlinedlist.android.feature.integrations.domain.ConnectedAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** UI state for the read-only "Connected accounts" screen. */
data class ConnectedAccountsUiState(
    val isLoading: Boolean = true,
    val accounts: List<ConnectedAccount> = emptyList(),
)

@HiltViewModel
class ConnectedAccountsViewModel @Inject constructor(
    private val repository: IntegrationsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectedAccountsUiState())
    val uiState: StateFlow<ConnectedAccountsUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val accounts = repository.getConnectedAccounts()
            _uiState.update { it.copy(isLoading = false, accounts = accounts) }
        }
    }
}
