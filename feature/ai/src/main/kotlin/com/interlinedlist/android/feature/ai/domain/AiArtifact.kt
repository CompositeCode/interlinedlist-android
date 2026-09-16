package com.interlinedlist.android.feature.ai.domain

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * The typed envelope the API calls an "artifact": the content an AI action
 * produced. `/suggest` returns one and `/generate` takes the same one back, so
 * it is held as the raw [payload] object — the bytes round-trip untouched
 * (including any key this client does not know about), and each AI surface
 * decodes the shape it asked for.
 */
data class AiArtifact(val payload: JsonObject) {

    /** `list`, `document`, `message_series`, `doc_series`, `message`, `thread`, or `tags`. */
    val kind: String?
        get() = (payload["kind"] as? JsonPrimitive)?.contentOrNull
}

/** Token accounting returned alongside a preview. Purely informational. */
data class AiUsage(
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val model: String? = null,
)
