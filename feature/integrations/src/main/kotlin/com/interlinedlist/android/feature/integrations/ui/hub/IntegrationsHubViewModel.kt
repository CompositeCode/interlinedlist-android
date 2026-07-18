package com.interlinedlist.android.feature.integrations.ui.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.integrations.data.IntegrationsRepository
import com.interlinedlist.android.feature.integrations.domain.PlanLimits
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the integrations hub. Plan limits are optional context shown at
 * the top; a failure to load them is non-fatal and simply hides the section, so
 * the hub's primary actions (export, connected accounts) stay usable.
 */
data class IntegrationsHubUiState(
    val isLoadingLimits: Boolean = true,
    val limits: PlanLimits? = null,
)

@HiltViewModel
class IntegrationsHubViewModel @Inject constructor(
    private val repository: IntegrationsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(IntegrationsHubUiState())
    val uiState: StateFlow<IntegrationsHubUiState> = _uiState.asStateFlow()

    init { loadLimits() }

    fun loadLimits() {
        _uiState.update { it.copy(isLoadingLimits = true) }
        viewModelScope.launch {
            val limits = when (val result = repository.getLimits()) {
                is ApiResult.Success -> result.data
                is ApiResult.Failure -> null
            }
            _uiState.update { it.copy(isLoadingLimits = false, limits = limits) }
        }
    }
}
