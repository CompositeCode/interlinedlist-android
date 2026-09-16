package com.interlinedlist.android.feature.lists.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

/** Expiry labels rendered against a fixed clock, zone and locale. */
class InviteLabelsTest {

    private val now: Instant = Instant.parse("2026-06-01T12:00:00Z")

    private fun label(iso: String?) =
        inviteExpiryLabel(iso, now = now, zone = ZoneOffset.UTC, locale = Locale.US)

    @Test
    fun `a missing expiry reads as no expiry`() {
        assertThat(label(null)).isEqualTo("No expiry")
        assertThat(label("  ")).isEqualTo("No expiry")
    }

    @Test
    fun `a future expiry reads as expires`() {
        assertThat(label("2026-06-12T09:00:00Z")).isEqualTo("Expires Jun 12, 2026")
    }

    @Test
    fun `a past expiry reads as expired`() {
        assertThat(label("2026-01-02T09:00:00Z")).isEqualTo("Expired Jan 2, 2026")
    }

    @Test
    fun `an unparseable expiry falls back to the raw value`() {
        assertThat(label("soon")).isEqualTo("Expires soon")
    }
}
