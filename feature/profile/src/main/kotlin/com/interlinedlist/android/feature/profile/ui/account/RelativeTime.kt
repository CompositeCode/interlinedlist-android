package com.interlinedlist.android.feature.profile.ui.account

import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Formats an ISO-8601 [isoTimestamp] as a coarse relative label ("just now",
 * "5 minutes ago", "3 days ago") relative to [now]. Returns [fallback] when the
 * timestamp is null or unparseable, so the settings rows always show something.
 *
 * Pure and side-effect free so it can be unit-tested without a device clock.
 */
fun relativeTime(
    isoTimestamp: String?,
    now: Instant = Instant.now(),
    fallback: String = "Never",
): String {
    val then = isoTimestamp?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return fallback
    val seconds = ChronoUnit.SECONDS.between(then, now).coerceAtLeast(0)
    return when {
        seconds < 60 -> "just now"
        seconds < 3_600 -> pluralize(seconds / 60, "minute")
        seconds < 86_400 -> pluralize(seconds / 3_600, "hour")
        seconds < 2_592_000 -> pluralize(seconds / 86_400, "day")
        seconds < 31_536_000 -> pluralize(seconds / 2_592_000, "month")
        else -> pluralize(seconds / 31_536_000, "year")
    }
}

private fun pluralize(value: Long, unit: String): String {
    val plural = if (value == 1L) unit else "${unit}s"
    return "$value $plural ago"
}
