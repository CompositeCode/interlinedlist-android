package com.interlinedlist.android.feature.documents.ui.powered

/**
 * The four ways a Powered Document can be drafted. The value goes out as
 * `context.mode` on `POST /api/ai/suggest`; the derived modes additionally need
 * the id/URL of their source, which the server resolves under the owning user.
 */
enum class PoweredDocumentMode(
    val apiValue: String,
    val label: String,
    /** Placeholder for the instruction field, worded for this mode. */
    val instructionHint: String,
) {
    /** No source — drafts from the described topic alone. */
    ARTICLE(
        apiValue = "article",
        label = "Standalone article",
        instructionHint = "What should the article cover?",
    ),

    /** Writes up one of the user's lists (`context.listId`). */
    FROM_LIST(
        apiValue = "from_list",
        label = "From a list",
        instructionHint = "How should this list be written up? (optional)",
    ),

    /** Writes a new document from an existing one (`context.documentId`). */
    FROM_ARTICLE(
        apiValue = "from_article",
        label = "From a document",
        instructionHint = "What should the new document do with it? (optional)",
    ),

    /** Researches a web page and cites it (`context.url`). */
    RESEARCH_URL(
        apiValue = "research_url",
        label = "Research a URL",
        instructionHint = "What should the write-up focus on? (optional)",
    );

    /**
     * Used as the instruction when the user leaves the field empty. `input` is
     * required by the endpoint, and for the three source-backed modes the source
     * already says most of what is needed — only [ARTICLE] genuinely has nothing
     * to go on, so it has no default and the UI insists on a topic.
     */
    val defaultInstruction: String?
        get() = when (this) {
            ARTICLE -> null
            FROM_LIST -> "Write an article based on this list."
            FROM_ARTICLE -> "Write a new article based on this document."
            RESEARCH_URL -> "Write an article based on this web page."
        }
}
