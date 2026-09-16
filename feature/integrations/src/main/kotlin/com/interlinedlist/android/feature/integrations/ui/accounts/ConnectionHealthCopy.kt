package com.interlinedlist.android.feature.integrations.ui.accounts

import com.interlinedlist.android.feature.integrations.domain.ConnectedAccount
import com.interlinedlist.android.feature.integrations.domain.ConnectionHealth
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Wording for the connected-accounts rows. Pure functions of an account and [now],
 * so the three health states can be asserted in plain unit tests and rendered
 * identically by the Composable.
 *
 * The rule throughout: say what the state *means* for the user, never just print a
 * timestamp. A raw "lastVerifiedAt: 2026-06-01" tells nobody that their next
 * LinkedIn cross-post is about to vanish.
 */

/** Short badge for a connection that needs attention, or null when there is nothing to flag. */
fun ConnectedAccount.healthBadge(now: Instant = Instant.now()): String? =
    when (healthAt(now)) {
        ConnectionHealth.STALE -> "Check connection"
        ConnectionHealth.NEVER_VERIFIED -> "Never verified"
        ConnectionHealth.FRESH, null -> null
    }

/**
 * The explanatory line under a linked account, or null when nothing is linked (the
 * row then just reads "Not connected").
 */
fun ConnectedAccount.healthLine(now: Instant = Instant.now()): String? =
    when (healthAt(now)) {
        null -> null
        ConnectionHealth.FRESH ->
            "Verified ${agoLabel(lastVerifiedAt, now) ?: "recently"} — this connection is working."
        ConnectionHealth.STALE -> {
            val age = agoLabel(lastVerifiedAt, now) ?: "over 30 days ago"
            "Last verified $age. It may have expired — ${atRiskClause()}"
        }
        ConnectionHealth.NEVER_VERIFIED -> {
            val connected = agoLabel(connectedAt, now)
            val prefix = if (connected == null) "Never verified." else "Connected $connected, never verified."
            "$prefix There is no sign it still works — ${atRiskClause()}"
        }
    }

/**
 * What goes wrong if this connection has lapsed. Cross-post targets fail *silently*
 * — the post publishes on InterlinedList and simply never reaches the network — which
 * is the whole reason the badge exists.
 */
private fun ConnectedAccount.atRiskClause(): String =
    if (provider.isCrossPostTarget) {
        "posts may stop reaching ${provider.label} without an error. Tap Verify to check it."
    } else {
        "${provider.label} features may stop working. Tap Verify to check it."
    }

/** Dialog title for the unlink confirmation. */
fun ConnectedAccount.unlinkTitle(): String = "Unlink ${provider.label}?"

/**
 * The consequence, stated plainly: unlinking a cross-post target turns syndication to
 * that network off. Losing that by accident is silent otherwise, so the dialog says it
 * before the tap, not after.
 */
fun ConnectedAccount.unlinkMessage(): String =
    if (provider.isCrossPostTarget) {
        "This stops cross-posting to ${provider.label}. New posts will no longer be sent " +
            "there. Anything already cross-posted stays where it is, and you can reconnect " +
            "on the InterlinedList website."
    } else {
        "This disconnects ${provider.label}. Its data will no longer be available in the " +
            "app until you reconnect on the InterlinedList website."
    }

/** Snackbar text confirming a completed unlink. */
fun ConnectedAccount.unlinkedMessage(): String =
    if (provider.isCrossPostTarget) {
        "${provider.label} unlinked. Cross-posting to ${provider.label} is off."
    } else {
        "${provider.label} unlinked."
    }

/** Snackbar text confirming a completed re-verification. */
fun ConnectedAccount.verifiedMessage(): String = "${provider.label} connection verified."

/**
 * A coarse "3 days ago" label for an ISO-8601 instant, or null when it is missing or
 * unparseable so callers can fall back to wording that doesn't pretend to know.
 */
private fun agoLabel(isoTimestamp: String?, now: Instant): String? {
    val then = isoTimestamp?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
    val days = ChronoUnit.DAYS.between(then, now).coerceAtLeast(0)
    return when {
        days == 0L -> "today"
        days == 1L -> "yesterday"
        days < 30L -> "$days days ago"
        days < 365L -> pluralize(days / 30, "month")
        else -> pluralize(days / 365, "year")
    }
}

private fun pluralize(value: Long, unit: String): String =
    "$value ${if (value == 1L) unit else "${unit}s"} ago"
