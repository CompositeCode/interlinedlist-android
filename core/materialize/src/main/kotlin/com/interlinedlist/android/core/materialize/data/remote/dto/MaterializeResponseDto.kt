package com.interlinedlist.android.core.materialize.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * `201 Created` from `POST /api/materialize`.
 *
 * Which members are present follows the requested `target`: `list` returns
 * `list`, `doc` returns `document`, `both` returns both, and `message` returns
 * `message` — a draft that was not posted. Everything is nullable here because
 * the presence rule belongs to the mapper, which knows which target was asked
 * for; a 201 that omits what the target promised is a contract violation, not a
 * shape this DTO should pretend to model.
 */
@Serializable
data class MaterializeResponseDto(
    val list: MaterializedListDto? = null,
    val document: MaterializedDocumentDto? = null,
    val message: MessageDraftDto? = null,
)

/**
 * The published example returns only `id` and `title`; the schema shows the
 * full list row. Everything past `id` is optional so either answer parses.
 */
@Serializable
data class MaterializedListDto(
    val id: String,
    val title: String? = null,
    val description: String? = null,
    val isPublic: Boolean? = null,
)

@Serializable
data class MaterializedDocumentDto(
    val id: String,
    val title: String? = null,
    val relativePath: String? = null,
    val isPublic: Boolean? = null,
)

/** The draft built for `target: "message"`. All four members are documented as required. */
@Serializable
data class MessageDraftDto(
    val content: String = "",
    val thread: List<String> = emptyList(),
    val isThread: Boolean = false,
    val charLimit: Int = 0,
)

/**
 * The API's error envelope, `{ "error": "…", "code": "…" }`.
 *
 * The shared `ErrorDto` in `:core:network` drops `code`, and this endpoint needs
 * it: its 403 is the subscriber gate except when the code says the account is
 * restricted/suspended/on probation, which a subscription would not fix.
 */
@Serializable
data class MaterializeErrorDto(
    val error: String? = null,
    val code: String? = null,
)

/** The documented machine-readable codes this endpoint can answer with. */
internal object MaterializeErrorCode {
    const val UNAUTHORIZED = "unauthorized"
    const val FORBIDDEN = "forbidden"
    const val SUBSCRIPTION_REQUIRED = "subscription_required"
    const val BAD_REQUEST = "bad_request"
    const val VALIDATION_FAILED = "validation_failed"
    const val NOT_FOUND = "not_found"
    const val RATE_LIMITED = "rate_limited"
    const val INTERNAL_ERROR = "internal_error"

    /** `account_restricted`, `account_suspended`, `account_probation_feature`, … */
    const val ACCOUNT_PREFIX = "account_"
}
