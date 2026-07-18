package com.interlinedlist.android.feature.messages.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant

class RelativeTimeTest {

    private val now = Instant.parse("2026-07-18T12:00:00Z")

    @Test
    fun `null or blank yields empty string`() {
        assertThat(relativeTime(null, now)).isEmpty()
        assertThat(relativeTime("", now)).isEmpty()
    }

    @Test
    fun `under a minute reads as just now`() {
        assertThat(relativeTime("2026-07-18T11:59:30Z", now)).isEqualTo("just now")
    }

    @Test
    fun `minutes hours days and weeks are abbreviated`() {
        assertThat(relativeTime("2026-07-18T11:55:00Z", now)).isEqualTo("5m")
        assertThat(relativeTime("2026-07-18T09:00:00Z", now)).isEqualTo("3h")
        assertThat(relativeTime("2026-07-16T12:00:00Z", now)).isEqualTo("2d")
        assertThat(relativeTime("2026-07-04T12:00:00Z", now)).isEqualTo("2w")
    }

    @Test
    fun `future timestamps clamp to just now`() {
        assertThat(relativeTime("2026-07-18T12:05:00Z", now)).isEqualTo("just now")
    }

    @Test
    fun `unparseable timestamp is returned verbatim`() {
        assertThat(relativeTime("not-a-date", now)).isEqualTo("not-a-date")
    }
}
