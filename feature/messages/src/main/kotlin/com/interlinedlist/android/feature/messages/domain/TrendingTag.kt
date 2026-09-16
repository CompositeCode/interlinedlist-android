package com.interlinedlist.android.feature.messages.domain

/**
 * One row of `GET /api/tags/trending`: a tag used on public messages inside the
 * requested trailing window, how many public messages used it there, and when it
 * was last used.
 *
 * Like [TagSuggestion], [tag] is a **free-form label** (spaces and punctuation
 * included) and is the exact string the tag feed queries by, so nothing here
 * trims, lowercases or tokenises it.
 */
data class TrendingTag(
    val tag: String,
    /** Public messages using this tag within the window; the ordering key. */
    val count: Int = 0,
    /**
     * ISO-8601 instant of the most recent public message carrying this tag, or
     * null when the API omitted it. Raw text: the UI formats it, nothing parses
     * it for logic.
     */
    val lastUsedAt: String? = null,
)

/**
 * The trailing window `GET /api/tags/trending?window=` counts over.
 *
 * The window is a **request** parameter, never part of the response: the live
 * payload is `{ tags: [ { tag, count, lastUsedAt } ] }` and says nothing about
 * the period it covers. The app therefore labels the surface from the window it
 * asked for, and an unknown value would be silently swallowed by the server
 * (which falls back to `week` without complaining) — so only these three
 * documented values may ever be sent.
 */
enum class TrendingWindow(
    /** The wire value for the `window` query parameter. */
    val wire: String,
    /** How to describe this window in the UI, e.g. "Trending this week". */
    val label: String,
) {
    DAY("day", "today"),
    WEEK("week", "this week"),
    MONTH("month", "this month"),
}
