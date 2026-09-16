package com.interlinedlist.android.feature.ai.domain

/** What `POST /api/ai/generate` persisted, plus the quota left afterwards. */
data class AiGeneration(
    val feature: AiFeature?,
    val created: AiCreated,
    val quota: AiQuota? = null,
)

/**
 * The `created` object, which takes one of four documented shapes depending on
 * the artifact that was persisted. [Unrecognised] covers a body this client
 * does not know how to read rather than failing the call — the write already
 * happened server-side.
 */
sealed interface AiCreated {

    /** `{ "listId": "…" }` — a `list` or a `message_series` persisted as a list. */
    data class ListCreated(val listId: String) : AiCreated

    /** `{ "documentId": "…" }` — a single document. */
    data class DocumentCreated(val documentId: String) : AiCreated

    /** `{ "folderId": "…", "documentIds": [...] }` — a `doc_series`. */
    data class DocumentSeriesCreated(
        val folderId: String,
        val documentIds: List<String>,
    ) : AiCreated

    /** `{ "scheduledMessageIds": [...], … }` — a series sent with `scheduleImmediately`. */
    data class ScheduledMessagesCreated(
        val messageIds: List<String>,
        val firstScheduledAt: String? = null,
        val lastScheduledAt: String? = null,
    ) : AiCreated

    /** The server reported no id this client recognises. */
    data object Unrecognised : AiCreated
}
