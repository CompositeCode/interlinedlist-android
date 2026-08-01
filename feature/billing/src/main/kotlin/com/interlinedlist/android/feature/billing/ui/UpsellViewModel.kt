package com.interlinedlist.android.feature.billing.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.billing.data.BillingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the upsell screen. [isLoading] disables both buttons while a
 * session is being minted; [errorMessage] renders inline when a request fails.
 */
data class UpsellUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * A Stripe hosted URL ready to be opened in a browser — a one-shot event. Emitting
 * the URL rather than launching it here keeps the ViewModel free of Android
 * `Intent`/`Context` and therefore unit-testable.
 */
data class OpenUrl(val url: String)

@HiltViewModel
class UpsellViewModel @Inject constructor(
    private val repository: BillingRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UpsellUiState())
    val uiState: StateFlow<UpsellUiState> = _uiState.asStateFlow()

    // Buffered channel so a URL event survives brief config-change gaps.
    private val _open = Channel<OpenUrl>(Channel.BUFFERED)
    val open = _open.receiveAsFlow()

    /** Mints a Checkout session; on success emits its URL for the screen to open. */
    fun subscribe(priceId: String? = null) = launchSession {
        repository.createCheckoutSession(priceId)
    }

    /** Mints a customer-portal session; on success emits its URL to open. */
    fun manageSubscription() = launchSession {
        repository.createPortalSession()
    }

    private fun launchSession(request: suspend () -> ApiResult<String>) {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = request()) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isLoading = false) }
                    _open.send(OpenUrl(result.data))
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
}
