package com.interlinedlist.android.feature.lists.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Renders an invite's expiry as a short label: "No expiry", "Expires Jun 12, 2026"
 * or, once the moment has passed, "Expired Jun 12, 2026". Falls back to the raw
 * value when it cannot be parsed, so an unexpected timestamp never blanks the row.
 *
 * [now], [zone] and [locale] are injectable to keep rendering deterministic in tests.
 */
fun inviteExpiryLabel(
    isoExpiresAt: String?,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String {
    if (isoExpiresAt.isNullOrBlank()) return "No expiry"
    val expiry = runCatching { Instant.parse(isoExpiresAt) }.getOrNull()
        ?: return "Expires $isoExpiresAt"
    val formatted = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(locale)
        .withZone(zone)
        .format(expiry)
    return if (expiry.isAfter(now)) "Expires $formatted" else "Expired $formatted"
}
