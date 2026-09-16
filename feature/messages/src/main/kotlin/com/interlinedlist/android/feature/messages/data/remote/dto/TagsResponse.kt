package com.interlinedlist.android.feature.messages.data.remote.dto

import com.interlinedlist.android.feature.messages.domain.TagSuggestion
import com.interlinedlist.android.feature.messages.domain.TrendingTag
import kotlinx.serialization.Serializable

/**
 * Response from `GET /api/tags/autocomplete?q=…`:
 * `{ "tags": [ { "tag": "lists", "count": 8 }, … ] }`.
 *
 * Suggestions arrive already ordered by count (descending), then alphabetically,
 * and already prefix-matched case-insensitively by the server — so the app keeps
 * the order and the contents exactly as sent.
 */
@Serializable
data class TagAutocompleteResponse(
    val tags: List<TagSuggestionDto> = emptyList(),
) {
    /** The suggestions as domain values, dropping any entry with no tag. */
    fun toDomain(): List<TagSuggestion> = tags.mapNotNull { it.toDomainOrNull() }
}

/** One `{ tag, count }` entry in an autocomplete (or trending) tag response. */
@Serializable
data class TagSuggestionDto(
    val tag: String = "",
    val count: Int = 0,
) {
    fun toDomainOrNull(): TagSuggestion? {
        val label = tag.takeIf { it.isNotBlank() } ?: return null
        return TagSuggestion(tag = label, count = count)
    }
}

/**
 * Response from `GET /api/tags/trending?window=…&limit=…`:
 * `{ "tags": [ { "tag": "Lego", "count": 2, "lastUsedAt": "2026-09-12T20:40:05.777Z" } ] }`.
 *
 * Verified live: there is **no window metadata on the response** and no `data`
 * envelope or pagination — the window is only ever something the caller asks
 * for. Rows arrive ordered by `count` (descending), then most-recently-used
 * first, and that order is preserved exactly as sent.
 */
@Serializable
data class TrendingTagsResponse(
    val tags: List<TrendingTagDto> = emptyList(),
) {
    /** The rows as domain values, dropping any entry with no usable tag. */
    fun toDomain(): List<TrendingTag> = tags.mapNotNull { it.toDomainOrNull() }
}

/** One `{ tag, count, lastUsedAt }` row of the trending response. */
@Serializable
data class TrendingTagDto(
    val tag: String = "",
    val count: Int = 0,
    val lastUsedAt: String? = null,
) {
    fun toDomainOrNull(): TrendingTag? {
        val label = tag.takeIf { it.isNotBlank() } ?: return null
        return TrendingTag(
            tag = label,
            count = count,
            lastUsedAt = lastUsedAt?.takeIf { it.isNotBlank() },
        )
    }
}
