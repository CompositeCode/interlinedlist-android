package com.interlinedlist.android.feature.messages.data.remote.dto

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

/**
 * The feed envelope is documented as `{ messages, pagination }` while the sibling
 * list endpoints wrap their rows in `data`. These tests pin the tolerant parsing
 * the DTO promises, plus the keyset `nextCursor` the feed pages on.
 */
class MessagesResponseTest {

    // Mirrors NetworkModule's configuration.
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    private fun decode(body: String) = json.decodeFromString(MessagesResponse.serializer(), body)

    @Test
    fun `reads the documented feed envelope and its next cursor`() {
        val response = decode(
            """
            {
              "messages": [ { "id": "m1", "content": "hello" } ],
              "pagination": { "limit": 20, "hasMore": true, "nextCursor": "b64|token=" }
            }
            """.trimIndent(),
        )

        assertThat(response.rows.map { it.id }).containsExactly("m1")
        assertThat(response.nextCursor).isEqualTo("b64|token=")
    }

    @Test
    fun `reads rows wrapped in the data envelope`() {
        val response = decode(
            """
            { "data": [ { "id": "m2", "content": "hi" } ],
              "pagination": { "limit": 20, "hasMore": false, "nextCursor": null } }
            """.trimIndent(),
        )

        assertThat(response.rows.map { it.id }).containsExactly("m2")
        assertThat(response.nextCursor).isNull()
    }

    @Test
    fun `a blank next cursor counts as the end of the feed`() {
        val response = decode(
            """{ "messages": [], "pagination": { "limit": 20, "hasMore": false, "nextCursor": "" } }""",
        )

        assertThat(response.rows).isEmpty()
        assertThat(response.nextCursor).isNull()
    }

    @Test
    fun `an empty body decodes to an empty page with no cursor`() {
        val response = decode("{}")

        assertThat(response.rows).isEmpty()
        assertThat(response.nextCursor).isNull()
    }
}
