package com.interlinedlist.android.feature.notifications.data.remote.dto

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class NotificationsResponseTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `reads the list from the data key`() {
        val response = json.decodeFromString(
            NotificationsResponse.serializer(),
            """{ "data": [ { "id": "1" }, { "id": "2" } ],
                "pagination": { "hasMore": true } }""",
        )
        assertThat(response.items.map { it.id }).containsExactly("1", "2").inOrder()
        assertThat(response.pagination.hasMore).isTrue()
    }

    @Test
    fun `falls back to the notifications key when data is absent`() {
        val response = json.decodeFromString(
            NotificationsResponse.serializer(),
            """{ "notifications": [ { "id": "a" } ] }""",
        )
        assertThat(response.items.map { it.id }).containsExactly("a")
    }

    @Test
    fun `reads the list from the live items key`() {
        // The production API returns `{ "unreadCount": N, "items": [...] }`. Regression
        // guard: this key was previously unmapped, silently emptying the notifications
        // feed and the background push poll.
        val response = json.decodeFromString(
            NotificationsResponse.serializer(),
            """{ "unreadCount": 3, "items": [ { "id": "x" }, { "id": "y" } ] }""",
        )
        assertThat(response.items.map { it.id }).containsExactly("x", "y").inOrder()
        assertThat(response.unreadCount).isEqualTo(3)
    }

    @Test
    fun `an empty body decodes with sane defaults`() {
        val response = json.decodeFromString(NotificationsResponse.serializer(), "{}")
        assertThat(response.items).isEmpty()
        assertThat(response.pagination.hasMore).isFalse()
        assertThat(response.pagination.limit).isEqualTo(PaginationDto.DEFAULT_LIMIT)
    }
}
