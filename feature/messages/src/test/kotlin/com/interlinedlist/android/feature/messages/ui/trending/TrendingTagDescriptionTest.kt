package com.interlinedlist.android.feature.messages.ui.trending

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.feature.messages.domain.TrendingTag
import org.junit.Test
import java.time.Instant

/**
 * The chip's spoken label. `lastUsedAt` is the only recency the payload actually
 * reports (there is no window metadata on the response), so this is where it is
 * used — and it has to survive rows that arrive without it.
 */
class TrendingTagDescriptionTest {

    private val now = Instant.parse("2026-09-16T18:00:00Z")

    @Test
    fun `reads the tag, its count and when it was last used`() {
        val description = trendingTagDescription(
            TrendingTag("Lego", count = 2, lastUsedAt = "2026-09-14T18:00:00Z"),
            now,
        )

        assertThat(description).isEqualTo("Lego, 2 messages, last used 2d")
    }

    @Test
    fun `a single message is not pluralised`() {
        val description = trendingTagDescription(TrendingTag("lists", count = 1), now)

        assertThat(description).isEqualTo("lists, 1 message")
    }

    @Test
    fun `a row missing its count and timestamp still reads as the tag`() {
        val description = trendingTagDescription(TrendingTag("life is short, o brave girl"), now)

        assertThat(description).isEqualTo("life is short, o brave girl")
    }
}
