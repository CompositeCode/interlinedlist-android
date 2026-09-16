package com.interlinedlist.android.feature.ai.domain

import com.interlinedlist.android.feature.ai.data.AiRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one observable every AI surface consults before drawing anything. Composer
 * toolbars, the Powered Templates tab and the Powered Document button all
 * collect [availability] and render nothing unless it [AiAvailability.isEnabled].
 *
 * Application-scoped so the five surfaces share a single `/api/ai/status` read
 * and a single quota figure.
 */
@Singleton
class AiGate @Inject constructor(
    private val repository: AiRepository,
) {

    private val _availability = MutableStateFlow<AiAvailability>(AiAvailability.Unknown)

    /** Starts at [AiAvailability.Unknown], which hides the AI surfaces too. */
    val availability: StateFlow<AiAvailability> = _availability.asStateFlow()

    /** Re-reads `/api/ai/status`. */
    suspend fun refresh(): AiAvailability =
        repository.availability().also { _availability.value = it }

    /** Reads status once; a no-op after the first successful resolution. */
    suspend fun ensureResolved(): AiAvailability =
        if (_availability.value is AiAvailability.Unknown) refresh() else _availability.value

    /**
     * Folds the quota echoed by `/suggest` and `/generate` back into the gate, so
     * every surface sees the same remaining allowance without re-reading status.
     */
    fun recordQuota(quota: AiQuota?) {
        val current = _availability.value
        if (quota != null && current is AiAvailability.Available) {
            _availability.value = AiAvailability.Available(quota)
        }
    }

    /**
     * Applies what a failed AI action revealed about availability: an
     * unconfigured provider or an expired/absent subscription takes the AI
     * surfaces down immediately instead of leaving a control that keeps failing.
     * Every other error is transient and leaves the gate alone.
     */
    fun recordFailure(error: AiError) {
        when (error) {
            is AiError.ProviderUnconfigured -> _availability.value = AiAvailability.Unavailable
            is AiError.NotSubscribed -> _availability.value = AiAvailability.NotSubscribed
            is AiError.QuotaExceeded -> {
                val current = _availability.value
                if (current is AiAvailability.Available) {
                    // Keep whatever limit we knew; the remainder is now zero.
                    _availability.value = AiAvailability.Available(
                        (current.quota ?: AiQuota()).copy(remaining = 0),
                    )
                }
            }
            else -> Unit
        }
    }
}
