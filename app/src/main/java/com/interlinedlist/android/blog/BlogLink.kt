package com.interlinedlist.android.blog

import java.net.URI

/**
 * Blog URLs, and the rules for turning a tapped link into one.
 *
 * The blog at `https://interlinedlist.com/blog` is **server-rendered** and the API
 * exposes no public listing or post endpoint (see issue #87), so this app has nothing
 * to render in-app: every blog destination resolves to a web URL that is handed to a
 * Custom Tab. That makes this object the whole "data layer" for the blog — deliberately.
 *
 * Kept as plain JVM code (no `android.net.Uri`) so the matching rules are covered by
 * fast unit tests, matching the `EmailChangeLink` precedent in `:feature:auth`.
 *
 * ## Which links the app claims
 *
 * The manifest registers **only** the app's own `interlinedlist://blog…` scheme. The
 * `https://interlinedlist.com/blog…` web URLs are deliberately *not* claimed: with no
 * in-app renderer, claiming them would intercept a browser-bound link only to hand it
 * straight back to a browser — losing the user's own browser session and, when no
 * Custom Tabs provider exists, resolving the plain `ACTION_VIEW` fallback back into
 * this very activity. [webUrlFor] still recognises the web form so in-app call sites
 * may pass either shape, and so the claim can be switched on in the manifest without
 * touching these rules if a real in-app renderer ever lands.
 */
object BlogLink {

    /** Host of the public site; also matched with a `www.` prefix. */
    const val WEB_HOST = "interlinedlist.com"

    /** Custom scheme the app registers for its own blog links. */
    const val APP_SCHEME = "interlinedlist"

    /** First path segment (web) / authority (custom scheme) identifying the blog. */
    const val BLOG_SEGMENT = "blog"

    /** Canonical URL of the blog index — the Account hub's Blog entry point. */
    const val INDEX_URL = "https://$WEB_HOST/$BLOG_SEGMENT"

    private val WEB_SCHEMES = setOf("https", "http")

    /** Canonical URL of a single post, by slug. */
    fun postUrl(slug: String): String = "$INDEX_URL/${slug.trim('/')}"

    /**
     * Resolves [uri] to the canonical blog URL it names, or null when it is not a blog
     * link at all.
     *
     * Recognised shapes (scheme/host case-insensitive; trailing slash, query and
     * fragment tolerated and dropped):
     * - `interlinedlist://blog` and `interlinedlist://blog/<slug>`
     * - `https://interlinedlist.com/blog` and `https://interlinedlist.com/blog/<slug>`
     *
     * Anything else — a foreign or look-alike host, a `/blogroll`-style prefix trap, a
     * `..` traversal segment, or a string that is not a URI at all — yields null rather
     * than an exception or a URL we would then open in a browser.
     */
    fun webUrlFor(uri: String?): String? {
        val trimmed = uri?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val parsed = runCatching { URI(trimmed) }.getOrNull() ?: return null

        val segments = parsed.blogPathSegments() ?: return null
        // Percent-encoding is preserved verbatim: the raw path is already a valid URL
        // component, and re-encoding it here would corrupt non-ASCII slugs.
        return if (segments.isEmpty()) INDEX_URL else "$INDEX_URL/${segments.joinToString("/")}"
    }

    /**
     * The post path under `/blog` (empty for the index itself), or null when this URI
     * is not a blog link.
     */
    private fun URI.blogPathSegments(): List<String>? {
        val scheme = scheme?.lowercase() ?: return null
        val segments = when {
            scheme in WEB_SCHEMES -> {
                val host = host?.lowercase()?.removePrefix("www.") ?: return null
                if (host != WEB_HOST) return null
                val path = rawPath.orEmpty().trim('/').splitPath()
                if (path.firstOrNull()?.lowercase() != BLOG_SEGMENT) return null
                path.drop(1)
            }
            scheme == APP_SCHEME -> {
                // `interlinedlist://blog/<slug>` — "blog" sits in the authority.
                if (host?.lowercase() != BLOG_SEGMENT) return null
                rawPath.orEmpty().trim('/').splitPath()
            }
            else -> return null
        }
        // A relative segment would silently point the browser somewhere other than the
        // blog, so refuse rather than normalise.
        if (segments.any { it == "." || it == ".." }) return null
        return segments
    }

    /** Splits an already-trimmed path, dropping the empty parts left by `//`. */
    private fun String.splitPath(): List<String> =
        if (isEmpty()) emptyList() else split('/').filter { it.isNotEmpty() }
}
