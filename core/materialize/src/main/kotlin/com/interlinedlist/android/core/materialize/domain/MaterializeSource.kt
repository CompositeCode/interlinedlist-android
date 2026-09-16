package com.interlinedlist.android.core.materialize.domain

/**
 * What a "Create from…" flow is converting, as an **id-only** reference.
 *
 * `POST /api/materialize` deliberately accepts nothing but ids: the server
 * re-fetches and re-authorizes every referenced id under the calling user and
 * rebuilds the source from its own data, so client-supplied cell values or body
 * text are never trusted. Referencing something the caller does not own is a
 * 404. Modelling the source as ids only makes it impossible for a caller to
 * smuggle content through this endpoint by accident.
 *
 * [DocumentSelection] is the one exception, and only because the selection has
 * no id of its own: the highlighted markdown is the selection's identity, and
 * the server still re-authorizes the document it came from.
 *
 * Each case carries exactly the ids its `kind` requires, so a `rows` source
 * without its owning `listId` — or a `document` source carrying message ids —
 * cannot be constructed.
 */
sealed interface MaterializeSource {

    /** The `source.kind` discriminator this case sends. */
    val kind: String

    /** One or more messages. Several messages combine into a single result. */
    data class Messages(val messageIds: List<String>) : MaterializeSource {
        init { require(messageIds.isNotEmpty()) { "A messages source needs at least one message id" } }

        override val kind: String get() = KIND

        companion object { const val KIND: String = "messages" }
    }

    /** One or more whole lists — schema and rows. */
    data class Lists(val listIds: List<String>) : MaterializeSource {
        init { require(listIds.isNotEmpty()) { "A lists source needs at least one list id" } }

        override val kind: String get() = KIND

        companion object { const val KIND: String = "lists" }
    }

    /** Selected rows from a single list; the rows cannot be separated from their list. */
    data class Rows(val listId: String, val rowIds: List<String>) : MaterializeSource {
        init {
            require(listId.isNotBlank()) { "A rows source needs the id of the list it came from" }
            require(rowIds.isNotEmpty()) { "A rows source needs at least one row id" }
        }

        override val kind: String get() = KIND

        companion object { const val KIND: String = "rows" }
    }

    /** A whole document. */
    data class Document(val documentId: String) : MaterializeSource {
        init { require(documentId.isNotBlank()) { "A document source needs a document id" } }

        override val kind: String get() = KIND

        companion object { const val KIND: String = "document" }
    }

    /**
     * A highlighted passage of a document, identified by the document id plus the
     * selected markdown. The wire `kind` is `docElements`.
     */
    data class DocumentSelection(
        val documentId: String,
        val markdown: String,
    ) : MaterializeSource {
        init {
            require(documentId.isNotBlank()) { "A document selection needs a document id" }
            require(markdown.isNotBlank()) { "A document selection needs the selected markdown" }
        }

        override val kind: String get() = KIND

        companion object { const val KIND: String = "docElements" }
    }
}
