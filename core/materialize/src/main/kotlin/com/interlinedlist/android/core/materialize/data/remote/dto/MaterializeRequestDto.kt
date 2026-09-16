package com.interlinedlist.android.core.materialize.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * `POST /api/materialize` body.
 *
 * ```
 * { "target": "list|doc|both|message",
 *   "source": { "kind": "messages", "messageIds": ["clx…"] },
 *   "listConfig": { … }, "docConfig": { … }, "messageConfig": { … } }
 * ```
 *
 * `target` and `source` are the only required members; the shared Json is
 * configured with `explicitNulls = false`, so an absent config is simply left
 * out of the body and the server applies its own defaults.
 */
@Serializable
data class MaterializeRequestDto(
    val target: String,
    val source: MaterializeSourceDto,
    val listConfig: ListConfigDto? = null,
    val docConfig: DocConfigDto? = null,
    val messageConfig: MessageConfigDto? = null,
)

/**
 * The id-only source descriptor. One DTO covers all five kinds because the
 * wire format is a flat object discriminated by `kind`; which id fields are
 * populated is decided by the domain [com.interlinedlist.android.core.materialize.domain.MaterializeSource]
 * case, so an invalid combination cannot be built here.
 */
@Serializable
data class MaterializeSourceDto(
    val kind: String,
    val messageIds: List<String>? = null,
    val listIds: List<String>? = null,
    val listId: String? = null,
    val rowIds: List<String>? = null,
    val documentId: String? = null,
    /** Only for `docElements`: the highlighted passage is the selection's identity. */
    val markdown: String? = null,
)

@Serializable
data class ListConfigDto(
    val title: String? = null,
    val description: String? = null,
    val isPublic: Boolean? = null,
    val fields: List<ListFieldDto>? = null,
    val includeData: Boolean? = null,
)

/**
 * One column definition.
 *
 * [sourceKey] is a [JsonElement] rather than a `String?` on purpose: a null
 * `sourceKey` is *meaningful* — it marks a user-added empty column — but the
 * shared Json runs with `explicitNulls = false`, which would drop a null
 * property from the body entirely. Holding `JsonNull` in a non-nullable
 * property forces the key to be written as an explicit `null`.
 */
@Serializable
data class ListFieldDto(
    val propertyKey: String,
    val propertyName: String,
    val propertyType: String,
    val sourceKey: JsonElement,
    val isRequired: Boolean? = null,
    val options: List<String>? = null,
)

@Serializable
data class DocConfigDto(
    val title: String? = null,
    val relativePath: String? = null,
    val isPublic: Boolean? = null,
    val listStyle: String? = null,
    val rowDataStyle: String? = null,
)

@Serializable
data class MessageConfigDto(
    val content: String? = null,
    val crossPostTargets: List<String>? = null,
    val allowThread: Boolean? = null,
    val publiclyVisible: Boolean? = null,
    val tags: List<String>? = null,
    val scheduledAt: String? = null,
)
