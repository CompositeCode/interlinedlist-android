package com.interlinedlist.android.feature.documents.ui.powered

import com.interlinedlist.android.feature.ai.domain.AiArtifact
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Reads the `document` artifact `powered_document` produces:
 * `{ kind, title, markdown, outline[], isPublic }`.
 *
 * `:feature:ai` deliberately keeps artifacts as raw JSON so it never has to know
 * about every feature's shape — each surface decodes the one it asked for, and
 * unknown keys survive the round trip back to `/generate` untouched.
 */
val AiArtifact.documentTitle: String?
    get() = payload.string("title")

val AiArtifact.documentMarkdown: String
    get() = payload.string("markdown").orEmpty()

val AiArtifact.documentOutline: List<String>
    get() = (payload["outline"] as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        ?.filter { it.isNotBlank() }
        .orEmpty()

/**
 * The same artifact with the user's title. Every other key — including any this
 * client does not model — is carried over verbatim, because `/generate`
 * re-validates the whole envelope.
 */
fun AiArtifact.withTitle(title: String): AiArtifact =
    AiArtifact(JsonObject(payload + ("title" to JsonPrimitive(title))))

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
