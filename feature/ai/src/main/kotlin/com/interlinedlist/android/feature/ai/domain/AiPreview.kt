package com.interlinedlist.android.feature.ai.domain

import kotlinx.serialization.json.JsonObject

/**
 * The input half of `POST /api/ai/suggest`. [context] is the endpoint's
 * feature-specific hint bag (`action`, `mode`, `listId`, `count`, …) — unknown
 * keys are ignored server-side and numeric hints are clamped, so each AI
 * surface builds the object it needs.
 */
data class AiSuggestInput(
    val input: String,
    val context: JsonObject? = null,
    /** Optional Anthropic model override; the server picks a default otherwise. */
    val model: String? = null,
    /** Requested output budget. Clamped down to the feature's ceiling, never up. */
    val maxOutputTokens: Int? = null,
)

/**
 * A preview produced by `/suggest`. **Nothing has been written.** The only way
 * to reach `/generate` is [confirm], which is what makes "nothing is saved
 * until the user approves it" a compile-time property rather than a convention
 * each AI surface has to remember.
 */
data class AiPreview(
    val feature: AiFeature,
    val artifact: AiArtifact,
    val usage: AiUsage? = null,
    val quota: AiQuota? = null,
) {
    /**
     * Marks this preview as approved by the user, optionally replacing the
     * artifact with an [edited] copy of it (the server re-validates either way).
     */
    fun confirm(edited: AiArtifact = artifact): ConfirmedPreview =
        ConfirmedPreview(preview = this, artifact = edited)
}

/**
 * An [AiPreview] the user has explicitly approved — the only thing
 * `AiRepository.generate` accepts. Its constructor is module-private, so no
 * caller outside this module can fabricate one without going through
 * [AiPreview.confirm].
 */
class ConfirmedPreview internal constructor(
    val preview: AiPreview,
    /** The artifact as confirmed — the preview's own, or the user's edit of it. */
    val artifact: AiArtifact,
) {
    /** The feature the artifact was produced for; sent back as the discriminator. */
    val feature: AiFeature get() = preview.feature
}

/**
 * Optional extras `POST /api/ai/generate` accepts. All of them are ignorable:
 * [scheduleImmediately] and [crossPost] only mean anything for
 * [AiFeature.MESSAGE_SERIES], and [model] is recorded in the audit ledger only.
 */
data class AiGenerateOptions(
    val scheduleImmediately: Boolean? = null,
    val crossPost: JsonObject? = null,
    val model: String? = null,
)
