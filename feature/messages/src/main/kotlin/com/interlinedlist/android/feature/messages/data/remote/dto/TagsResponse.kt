package com.interlinedlist.android.feature.messages.data.remote.dto

import com.interlinedlist.android.feature.messages.domain.TagSuggestion
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
