package com.interlinedlist.android.feature.messages.data.remote.dto

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

/**
 * `GET /api/tags/trending` answers with a bare `{ "tags": [ … ] }` — no `data`
 * envelope, no pagination, and (verified live) **no window metadata**: the
 * trailing window is something the caller *asks* for, never something the
 * response reports back. These tests pin that shape and the defensive parsing
 * around it, because tags are free-form user text: real ones contain spaces,
 * commas and mixed case.
 */
class TrendingTagsResponseTest {

    // Mirrors NetworkModule's configuration.
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    private fun decode(body: String) = json.decodeFromString(TrendingTagsResponse.serializer(), body)

    @Test
    fun `reads the live payload, keeping the server's order and its odd tags`() {
        val response = decode(
            """
            {
              "tags": [
                { "tag": "Lego", "count": 2, "lastUsedAt": "2026-09-12T20:40:05.777Z" },
                { "tag": "nuclear god", "count": 2, "lastUsedAt": "2026-09-11T03:00:14.845Z" },
                { "tag": "life is short, o brave girl", "count": 1,
                  "lastUsedAt": "2026-09-11T03:44:52.334Z" }
              ]
            }
            """.trimIndent(),
        )

        // Spaces, a comma and mixed case all survive verbatim: the tag is the key
        // the tag feed queries by, so any normalisation here would break the link.
        assertThat(response.toDomain().map { it.tag })
            .containsExactly("Lego", "nuclear god", "life is short, o brave girl")
            .inOrder()
        assertThat(response.toDomain().first().count).isEqualTo(2)
        assertThat(response.toDomain().first().lastUsedAt).isEqualTo("2026-09-12T20:40:05.777Z")
    }

    @Test
    fun `a row missing or nulling fields still parses`() {
        val response = decode(
            """
            {
              "tags": [
                { "tag": "quiet" },
                { "tag": "nulled", "count": null, "lastUsedAt": null }
              ]
            }
            """.trimIndent(),
        )

        val tags = response.toDomain()
        assertThat(tags.map { it.tag }).containsExactly("quiet", "nulled").inOrder()
        assertThat(tags.map { it.count }).containsExactly(0, 0)
        assertThat(tags.map { it.lastUsedAt }).containsExactly(null, null)
    }

    @Test
    fun `rows without a usable tag are dropped rather than rendered blank`() {
        val response = decode(
            """{ "tags": [ { "count": 9 }, { "tag": "   ", "count": 4 }, { "tag": "real" } ] }""",
        )

        assertThat(response.toDomain().map { it.tag }).containsExactly("real")
    }

    @Test
    fun `unknown keys the API may add later are ignored`() {
        val response = decode(
            """
            {
              "window": "week",
              "tags": [ { "tag": "lists", "count": 6, "score": 0.42 } ]
            }
            """.trimIndent(),
        )

        assertThat(response.toDomain().map { it.tag }).containsExactly("lists")
    }

    @Test
    fun `an empty or absent tag list decodes to no tags`() {
        assertThat(decode("""{ "tags": [] }""").toDomain()).isEmpty()
        assertThat(decode("{}").toDomain()).isEmpty()
    }
}
