package com.interlinedlist.android.feature.ai.domain

/**
 * The `feature` discriminator both `POST /api/ai/suggest` and
 * `POST /api/ai/generate` require in their body. This is the single place the
 * wire strings live; an AI surface names its case here instead of spelling the
 * string out at the call site.
 *
 * Each feature produces its own artifact kind (see [AiArtifact]):
 * `writing_assist` → `message`/`thread`/`tags` (never persistable),
 * `powered_template` → `list`, `powered_document` → `document`,
 * `message_series` → `message_series`, `article_series` → `doc_series`.
 */
enum class AiFeature(val apiValue: String) {
    /** Rewrite/tighten/expand/grammar/thread/tag a composer draft. */
    WRITING_ASSIST("writing_assist"),

    /** Generate a list (schema + starter rows) from a description. */
    POWERED_TEMPLATE("powered_template"),

    /** Draft a single markdown document (article / from list / from doc / URL). */
    POWERED_DOCUMENT("powered_document"),

    /** Plan a series of short, connected messages. */
    MESSAGE_SERIES("message_series"),

    /** Plan and write a coherent series of documents. */
    ARTICLE_SERIES("article_series");

    companion object {
        /** Maps a wire string (or null) back to a feature, or null when unknown. */
        fun fromApiValue(value: String?): AiFeature? =
            entries.firstOrNull { it.apiValue == value }
    }
}
