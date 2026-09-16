package com.interlinedlist.android.feature.ai.ui

import com.interlinedlist.android.feature.ai.domain.AiError
import com.interlinedlist.android.feature.ai.domain.AiGeneration
import com.interlinedlist.android.feature.ai.domain.AiPreview

/**
 * The preview → confirm contract every AI surface renders. Its shape is the
 * guarantee the help centre makes: an action can only reach [Generating] by way
 * of [Previewing], so nothing is written until the user approves what they see.
 */
sealed interface AiPreviewState {

    /** No AI action in flight; nothing to show. */
    data object Idle : AiPreviewState

    /** `/suggest` is running. */
    data object Suggesting : AiPreviewState

    /** A preview is on screen awaiting approval. **Nothing has been written.** */
    data class Previewing(val preview: AiPreview) : AiPreviewState

    /** The user approved; `/generate` is running. */
    data class Generating(val preview: AiPreview) : AiPreviewState

    /** The artifact was persisted. [AiGeneration.created] carries the new id(s). */
    data class Generated(val generation: AiGeneration) : AiPreviewState

    /** The action failed. [preview] is kept when one was already on screen. */
    data class Failed(val error: AiError, val preview: AiPreview? = null) : AiPreviewState

    /** True while either leg of the flow is in flight. */
    val isBusy: Boolean
        get() = this is Suggesting || this is Generating
}
