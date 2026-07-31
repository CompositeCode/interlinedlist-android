package com.interlinedlist.android.feature.profile.ui

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.feature.profile.ui.account.relativeTime
import org.junit.Test
import java.time.Instant

class RelativeTimeTest {

    private val now = Instant.parse("2026-07-31T12:00:00.000Z")

    @Test
    fun `null timestamp returns the fallback`() {
        assertThat(relativeTime(null, now = now)).isEqualTo("Never")
    }

    @Test
    fun `unparseable timestamp returns the fallback`() {
        assertThat(relativeTime("not-a-date", now = now)).isEqualTo("Never")
    }

    @Test
    fun `under a minute reads as just now`() {
        assertThat(relativeTime("2026-07-31T11:59:30.000Z", now = now)).isEqualTo("just now")
    }

    @Test
    fun `minutes are pluralized`() {
        assertThat(relativeTime("2026-07-31T11:59:00.000Z", now = now)).isEqualTo("1 minute ago")
        assertThat(relativeTime("2026-07-31T11:55:00.000Z", now = now)).isEqualTo("5 minutes ago")
    }

    @Test
    fun `hours and days are formatted`() {
        assertThat(relativeTime("2026-07-31T09:00:00.000Z", now = now)).isEqualTo("3 hours ago")
        assertThat(relativeTime("2026-07-28T12:00:00.000Z", now = now)).isEqualTo("3 days ago")
    }

    @Test
    fun `a future timestamp is clamped to just now`() {
        assertThat(relativeTime("2026-07-31T12:05:00.000Z", now = now)).isEqualTo("just now")
    }
}
