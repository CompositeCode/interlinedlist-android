package com.interlinedlist.android.feature.messages.domain

/**
 * One autocomplete suggestion from `GET /api/tags/autocomplete`: an existing
 * public tag and how many public messages already use it.
 *
 * A tag is a **free-form label**, not a hashtag token: the live endpoint happily
 * returns values containing spaces and punctuation (e.g.
 * `"life is short, o brave girl"`), so nothing here may tokenise or normalise it.
 * The server matches a **case-insensitive literal prefix** against [tag]; the app
 * shows exactly what the server returned and never filters or fuzzy-matches on
 * top of it, which would show suggestions the server would never have given.
 */
data class TagSuggestion(
    val tag: String,
    /** Public messages using this tag; the server orders suggestions by it. */
    val count: Int = 0,
)
