package com.interlinedlist.android.feature.messages.data.local

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.feature.messages.domain.PushedMessage
import org.junit.Test

/**
 * The composite columns are stored as JSON, so the embedded original of a cached
 * push/quote has to survive the round trip — otherwise an offline feed would
 * render a push as an empty card.
 */
class MessageConvertersTest {

    private val converters = MessageConverters()

    @Test
    fun `round-trips the embedded original`() {
        val original = PushedMessage(
            id = "orig",
            content = "the original post",
            authorUsername = "quinn",
            authorDisplayName = "Quinn",
            authorAvatarUrl = "https://cdn/q.png",
            createdAt = "2026-07-18T09:00:00Z",
        )

        val restored = converters.jsonToPushedMessage(converters.pushedMessageToJson(original))

        assertThat(restored).isEqualTo(original)
    }

    @Test
    fun `stores no embedded original for an ordinary message`() {
        assertThat(converters.pushedMessageToJson(null)).isNull()
        assertThat(converters.jsonToPushedMessage(null)).isNull()
        assertThat(converters.jsonToPushedMessage("")).isNull()
    }

    @Test
    fun `an unreadable stored value degrades to no original rather than crashing`() {
        assertThat(converters.jsonToPushedMessage("not json")).isNull()
    }
}
