package com.interlinedlist.android.feature.ai.ui

import com.interlinedlist.android.feature.ai.data.AiRepository
import com.interlinedlist.android.feature.ai.domain.AiArtifact
import com.interlinedlist.android.feature.ai.domain.AiFeature
import com.interlinedlist.android.feature.ai.domain.AiGate
import com.interlinedlist.android.feature.ai.domain.AiGenerateOptions
import com.interlinedlist.android.feature.ai.domain.AiResult
import com.interlinedlist.android.feature.ai.domain.AiSuggestInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives one AI action through preview → confirm, so the five AI surfaces share
 * the rule rather than each re-implementing it. A view model owns one of these
 * per surface and passes its own `viewModelScope`:
 *
 * ```
 * private val ai = AiPreviewSession(repository, gate, viewModelScope)
 * ```
 *
 * [confirm] is the only path to `/generate`, and it only works from
 * [AiPreviewState.Previewing] — there is no way to write straight from a
 * suggestion, and no second write while one is in flight.
 */
class AiPreviewSession(
    private val repository: AiRepository,
    private val gate: AiGate,
    private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow<AiPreviewState>(AiPreviewState.Idle)
    val state: StateFlow<AiPreviewState> = _state.asStateFlow()

    /**
     * Runs `/suggest` for [feature]. Ignored while another action is in flight,
     * returning false so the caller knows nothing started.
     */
    fun suggest(feature: AiFeature, input: AiSuggestInput): Boolean {
        if (_state.value.isBusy) return false
        _state.value = AiPreviewState.Suggesting
        scope.launch {
            when (val result = repository.suggest(feature, input)) {
                is AiResult.Success -> {
                    gate.recordQuota(result.data.quota)
                    _state.value = AiPreviewState.Previewing(result.data)
                }
                is AiResult.Failure -> {
                    gate.recordFailure(result.error)
                    _state.value = AiPreviewState.Failed(result.error)
                }
            }
        }
        return true
    }

    /**
     * Approves the preview currently on screen and persists it, optionally
     * replacing the artifact with the user's [edited] version.
     *
     * Returns false — and calls nothing — unless a preview is actually being
     * shown, which is what stops a write without approval.
     */
    fun confirm(
        edited: AiArtifact? = null,
        options: AiGenerateOptions = AiGenerateOptions(),
    ): Boolean {
        val previewing = _state.value as? AiPreviewState.Previewing ?: return false
        val preview = previewing.preview
        val confirmed = preview.confirm(edited ?: preview.artifact)
        _state.value = AiPreviewState.Generating(preview)
        scope.launch {
            when (val result = repository.generate(confirmed, options)) {
                is AiResult.Success -> {
                    gate.recordQuota(result.data.quota)
                    _state.value = AiPreviewState.Generated(result.data)
                }
                is AiResult.Failure -> {
                    gate.recordFailure(result.error)
                    _state.value = AiPreviewState.Failed(result.error, preview)
                }
            }
        }
        return true
    }

    /** Discards the preview without writing anything, and clears any error. */
    fun discard() {
        if (!_state.value.isBusy) _state.value = AiPreviewState.Idle
    }
}
