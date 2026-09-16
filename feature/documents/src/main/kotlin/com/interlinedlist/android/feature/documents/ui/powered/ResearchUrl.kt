package com.interlinedlist.android.feature.documents.ui.powered

import java.net.URI

/**
 * Local validation for the Research URL mode.
 *
 * The check is worth doing on the client because an AI call is not free: a
 * rejected `/suggest` still consumes one of the 50 daily generations, so a
 * typo'd address must be refused before any request is issued. The server-side
 * fetcher only accepts `http`/`https` (and is SSRF-guarded), so the same two
 * schemes are all this accepts.
 */
object ResearchUrl {

    /** Returns the trimmed URL when it is one the server would fetch, else null. */
    fun normalise(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host ?: return null
        // A bare hostname with no dot is either a local name or a typo; the
        // fetcher would refuse it, so do not spend a generation finding out.
        if (!host.contains('.') || host.startsWith('.') || host.endsWith('.')) return null
        return trimmed
    }

    /** True when [raw] is an address the Research URL mode can be run against. */
    fun isValid(raw: String): Boolean = normalise(raw) != null
}
