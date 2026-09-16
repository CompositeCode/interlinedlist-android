package com.interlinedlist.android.feature.messages.navigation

import java.net.URI
import java.net.URLDecoder

/**
 * A tapped "show me this tag" link.
 *
 * The web renders every tag on a message card as a link to the home feed with a
 * `tag` query parameter — `<a href={"/?tag=" + encodeURIComponent(tag)}>` — so the
 * canonical tag URL is `https://interlinedlist.com/?tag=<tag>` and nothing else:
 * `/tag/<tag>` and `/tags/<tag>` both 404 on the live site.
 *
 * Kept as plain JVM code (no `android.net.Uri`) so the parsing rules are covered by
 * fast unit tests, exactly like the auth module's `EmailChangeLink`.
 */
object TagFeedLink {

    /** Query parameter carrying the tag, on both the web and app-scheme links. */
    const val TAG_PARAM = "tag"

    /** Host of the web links; also matched with a `www.` prefix. */
    const val WEB_HOST = "interlinedlist.com"

    /** Custom scheme the app registers for the same link. */
    const val APP_SCHEME = "interlinedlist"

    /** Authority of the custom-scheme form, `interlinedlist://tag?tag=…`. */
    const val APP_AUTHORITY = "tag"

    private val WEB_SCHEMES = setOf("https", "http")

    /**
     * Parses [uri] into the tag it selects, or returns null when it is not a tag
     * link.
     *
     * Recognised shapes (scheme/host case-insensitive, extra query parameters
     * tolerated):
     * - `https://interlinedlist.com/?tag=…` (and without the trailing slash)
     * - `interlinedlist://tag?tag=…`
     *
     * The tag is returned **percent-decoded and verbatim otherwise**: tags are
     * free-form and legitimately contain spaces, commas and case
     * ("life is short, o brave girl" is a real one), so nothing here trims,
     * lowercases or splits them.
     *
     * A link to any other path, a foreign host, a missing or blank tag, or a
     * string that is not a URI at all yields null rather than an exception — a
     * deep link must never crash the launch.
     */
    fun parse(uri: String?): String? {
        val trimmed = uri?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val parsed = runCatching { URI(trimmed) }.getOrNull() ?: return null
        if (!parsed.isTagTarget()) return null
        return parsed.rawQuery.queryParam(TAG_PARAM)?.takeIf { it.isNotBlank() }
    }

    /** True when this URI addresses the tag surface (and not some other page). */
    private fun URI.isTagTarget(): Boolean {
        val scheme = scheme?.lowercase() ?: return false
        return when {
            scheme in WEB_SCHEMES -> {
                val host = host?.lowercase()?.removePrefix("www.") ?: return false
                // The tag feed is the site *root* with a `tag` query, so any other
                // path (a shared list, a profile) is deliberately not a tag link.
                host == WEB_HOST && path.orEmpty().trim('/').isEmpty()
            }
            scheme == APP_SCHEME ->
                (host ?: authority).orEmpty().lowercase() == APP_AUTHORITY &&
                    path.orEmpty().trim('/').isEmpty()
            else -> false
        }
    }

    /** Reads a single percent-decoded query parameter out of a raw query string. */
    private fun String?.queryParam(name: String): String? = this
        ?.split('&')
        ?.firstNotNullOfOrNull { pair ->
            val separator = pair.indexOf('=')
            if (separator <= 0) return@firstNotNullOfOrNull null
            val key = pair.substring(0, separator).decodeOrNull()
            if (!key.equals(name, ignoreCase = true)) return@firstNotNullOfOrNull null
            pair.substring(separator + 1).decodeOrNull()
        }

    /** Percent-decodes a query component, falling back to the raw text. */
    private fun String.decodeOrNull(): String? =
        runCatching { URLDecoder.decode(this, Charsets.UTF_8.name()) }.getOrDefault(this)
}
