package com.interlinedlist.android.feature.messages.ui

import java.time.Duration
import java.time.Instant

/**
 * Formats an ISO-8601 instant as a short relative label ("just now", "5m", "3h",
 * "2d", "4w"). Falls back to the raw string when it cannot be parsed, and to an
 * empty string when null, so the UI never crashes on unexpected timestamps.
 *
 * [now] is injectable to keep the mapping deterministic in tests.
 */
fun relativeTime(isoTimestamp: String?, now: Instant = Instant.now()): String {
    if (isoTimestamp.isNullOrBlank()) return ""
    val then = runCatching { Instant.parse(isoTimestamp) }.getOrElse {
        return@relativeTime isoTimestamp
    }
    val seconds = Duration.between(then, now).seconds
    if (seconds < 0) return "just now"
    return when {
        seconds < 60 -> "just now"
        seconds < 3_600 -> "${seconds / 60}m"
        seconds < 86_400 -> "${seconds / 3_600}h"
        seconds < 604_800 -> "${seconds / 86_400}d"
        else -> "${seconds / 604_800}w"
    }
}
