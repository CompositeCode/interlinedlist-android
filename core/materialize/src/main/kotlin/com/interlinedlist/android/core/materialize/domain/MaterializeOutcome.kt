package com.interlinedlist.android.core.materialize.domain

/**
 * What a conversion produced.
 *
 * **This is authoritative, not an echo of the preview.** Only ids went over the
 * wire; the server rebuilt everything from its own data, so a result may
 * legitimately differ from what was previewed if the source changed mid-flow.
 * Render what came back.
 *
 * One case per destination, so a caller reads the created objects without
 * null-checking keys the target never promised.
 */
sealed interface MaterializeOutcome {

    /** `To List`. */
    data class ListCreated(val list: MaterializedList) : MaterializeOutcome

    /** `To Doc`. */
    data class DocumentCreated(val document: MaterializedDocument) : MaterializeOutcome

    /** `To List & Doc`. */
    data class ListAndDocumentCreated(
        val list: MaterializedList,
        val document: MaterializedDocument,
    ) : MaterializeOutcome

    /**
     * `To Message`. Nothing was created — hand [draft] to the composer, which
     * posts it through `POST /api/messages`.
     */
    data class DraftReady(val draft: MessageDraft) : MaterializeOutcome
}

/**
 * The list a conversion created. Only [id] and [title] are guaranteed by the
 * published example; the rest are modelled defensively as nullable so a thinner
 * response still parses and the caller can at least open what was made.
 */
data class MaterializedList(
    val id: String,
    val title: String,
    val description: String? = null,
    val isPublic: Boolean? = null,
)

/** The document a conversion created. Nullable beyond [id]/[title] for the same reason. */
data class MaterializedDocument(
    val id: String,
    val title: String,
    val relativePath: String? = null,
    val isPublic: Boolean? = null,
)

/**
 * The body the message destination built. **Nothing has been posted.**
 *
 * [charLimit] is the smaller of the caller's own `maxMessageLength` and the
 * tightest selected cross-post channel, so the composer shows the limit that
 * will actually apply rather than previewing one post and silently sending a
 * three-part thread.
 */
data class MessageDraft(
    val content: String,
    /** The body split to [charLimit], in reply order. One entry when it already fits. */
    val thread: List<String>,
    val isThread: Boolean,
    val charLimit: Int,
)
