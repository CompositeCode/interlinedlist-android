package com.interlinedlist.android.core.materialize.domain

/**
 * The `target` discriminator `POST /api/materialize` requires. This is the one
 * place the wire strings live.
 */
enum class MaterializeTarget(val apiValue: String) {
    /** A new list, one row per item. */
    LIST("list"),

    /** A new markdown document. */
    DOC("doc"),

    /** Both, in a single step. */
    BOTH("both"),

    /**
     * A message **draft**. This target creates nothing: it is still a server
     * call — the server builds the body from the source and sizes it — but the
     * response is a draft that the composer must post through
     * `POST /api/messages`, which is where every posting gate lives.
     */
    MESSAGE("message");

    /**
     * True for the three targets that write. The subscriber gate applies to
     * these; see `MaterializeGate`.
     */
    val createsContent: Boolean get() = this != MESSAGE
}

/**
 * One confirmed "Create from…" conversion: a [MaterializeSource] plus the
 * destination it is going to, carrying **only** the configuration that
 * destination accepts.
 *
 * The four cases are the four destinations offered on every entry point. Each
 * one owns its own config, so a document title cannot be attached to a
 * list-only conversion and a cross-post channel cannot be attached to something
 * that is not a draft — the request/target/config matrix is closed by the type
 * rather than validated at runtime.
 *
 * Note that [ToMessageDraft] is *not* a client-side prefill: it is the same
 * endpoint, and the server derives and sizes the body. What differs is that it
 * returns a [MaterializeOutcome.DraftReady] and persists nothing, which is why
 * [MaterializeTarget.createsContent] is false for it.
 *
 * Every combination of source and destination the API accepts is representable.
 * The only combination the product hides is messages → message, which the web
 * UI omits in favour of Quote/Push; the API accepts it, so it is not excluded
 * here — an entry point simply does not offer it.
 */
sealed interface MaterializeRequest {

    val source: MaterializeSource
    val target: MaterializeTarget

    /**
     * `To List` — a new list, one row per item. [listConfig] is not optional:
     * the server requires a list title.
     */
    data class ToList(
        override val source: MaterializeSource,
        val listConfig: ListConfig,
    ) : MaterializeRequest {
        override val target: MaterializeTarget get() = MaterializeTarget.LIST
    }

    /** `To Doc` — a new markdown document. */
    data class ToDocument(
        override val source: MaterializeSource,
        val docConfig: DocConfig? = null,
    ) : MaterializeRequest {
        override val target: MaterializeTarget get() = MaterializeTarget.DOC
    }

    /**
     * `To List & Doc` — both, in one step, from one source. The list half still
     * needs its title; the document half can be left to the server.
     */
    data class ToListAndDocument(
        override val source: MaterializeSource,
        val listConfig: ListConfig,
        val docConfig: DocConfig? = null,
    ) : MaterializeRequest {
        override val target: MaterializeTarget get() = MaterializeTarget.BOTH
    }

    /** `To Message` — returns a draft for the composer and creates nothing. */
    data class ToMessageDraft(
        override val source: MaterializeSource,
        val messageConfig: MessageDraftConfig? = null,
    ) : MaterializeRequest {
        override val target: MaterializeTarget get() = MaterializeTarget.MESSAGE
    }
}
