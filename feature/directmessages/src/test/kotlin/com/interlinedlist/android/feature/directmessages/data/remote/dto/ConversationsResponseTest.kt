package com.interlinedlist.android.feature.directmessages.data.remote.dto

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

/**
 * `GET /api/dm/conversations` is not modelled in the OpenAPI spec nor documented
 * in the help centre, so these tests pin the tolerant parsing the DTO promises.
 */
class ConversationsResponseTest {

    // Mirrors NetworkModule's configuration.
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    private fun decode(body: String) =
        json.decodeFromString(ConversationsResponse.serializer(), body)

    @Test
    fun `parses one row per conversation with a nested last message`() {
        val response = decode(
            """
            {
              "items": [
                {
                  "pairKey": "u1:u2",
                  "otherUser": {"id":"u2","username":"adron","displayName":"Adron","avatar":"http://a"},
                  "lastMessage": {
                    "id":"dm_010","pairKey":"u1:u2","senderId":"u2","recipientId":"u1",
                    "body":"see the new grid","createdAt":"2026-09-13T11:00:00.000Z","readAt":null
                  },
                  "unreadCount": 2
                }
              ],
              "nextCursor": "dm_003"
            }
            """.trimIndent(),
        )

        assertThat(response.rows).hasSize(1)
        assertThat(response.nextCursor).isEqualTo("dm_003")
        val row = response.rows.first()
        assertThat(row.pairKey).isEqualTo("u1:u2")
        assertThat(row.participant?.username).isEqualTo("adron")
        assertThat(row.previewText).isEqualTo("see the new grid")
        assertThat(row.lastActivityAt).isEqualTo("2026-09-13T11:00:00.000Z")
        assertThat(row.newestMessageId).isEqualTo("dm_010")
        assertThat(row.unreadCount).isEqualTo(2)
    }

    @Test
    fun `parses a flattened row that uses preview and lastMessageAt`() {
        val response = decode(
            """
            {
              "conversations": [
                {
                  "pairKey": "u1:u3",
                  "user": {"id":"u3","username":"blake"},
                  "lastMessageId": "dm_020",
                  "preview": "short excerpt",
                  "lastMessageAt": "2026-09-12T08:30:00.000Z",
                  "hasUnread": true
                }
              ]
            }
            """.trimIndent(),
        )

        val row = response.rows.single()
        assertThat(row.participant?.username).isEqualTo("blake")
        assertThat(row.previewText).isEqualTo("short excerpt")
        assertThat(row.lastActivityAt).isEqualTo("2026-09-12T08:30:00.000Z")
        assertThat(row.newestMessageId).isEqualTo("dm_020")
        assertThat(row.hasUnread).isTrue()
        assertThat(response.nextCursor).isNull()
    }

    @Test
    fun `tolerates explicit nulls, missing fields and unknown keys`() {
        val response = decode(
            """
            {
              "items": [
                {
                  "pairKey": null,
                  "otherUser": {"id":"u4","username":"casey","displayName":null,"avatar":null},
                  "lastMessage": null,
                  "unreadCount": null,
                  "hasUnread": null,
                  "mutedUntil": "2026-10-01T00:00:00.000Z",
                  "somethingNobodyModelledYet": {"nested": [1, 2, 3]}
                },
                {}
              ],
              "nextCursor": null,
              "totalPages": 4
            }
            """.trimIndent(),
        )

        assertThat(response.rows).hasSize(2)
        val known = response.rows.first()
        assertThat(known.participant?.username).isEqualTo("casey")
        assertThat(known.previewText).isEmpty()
        assertThat(known.lastActivityAt).isNull()
        assertThat(known.unreadCount).isNull()

        // A completely empty row still decodes; the mapper is what drops it.
        val empty = response.rows.last()
        assertThat(empty.participant).isNull()
        assertThat(empty.newestMessageId).isEmpty()
    }

    @Test
    fun `an empty body decodes to an empty inbox`() {
        val response = decode("{}")

        assertThat(response.rows).isEmpty()
        assertThat(response.nextCursor).isNull()
    }
}
