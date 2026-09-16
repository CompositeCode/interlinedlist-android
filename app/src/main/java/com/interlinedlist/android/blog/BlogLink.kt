package com.interlinedlist.android.blog

import java.net.URI
import java.net.URLDecoder

/** Which half of the blog email-list flow an emailed link performs. */
enum class BlogSubscriptionAction {
    /** Double opt-in confirmation — the link in the "confirm your subscription" email. */
    CONFIRM,

    /** The human-facing unsubscribe link in every blog email's footer. */
    UNSUBSCRIBE,
}

/**
 * A parsed blog email-list deep link: which [action] the tapped link performs and the
 * one-time [token] it carries.
 *
 * [token] may be **empty**. The app claims these URLs in the manifest, so once a link
 * is recognised it must always be handled — dropping a tokenless or hand-truncated URL
 * on the floor would leave the user staring at the app's home screen with no idea
 * whether anything happened. An empty token is reported to the user as an unusable
 * link instead (see `BlogSubscriptionViewModel`), and never sent to the server.
 */
data class BlogSubscriptionLink(
    val action: BlogSubscriptionAction,
    val token: String,
)

/** What the server said about a confirm/unsubscribe token. */
enum class BlogSubscriptionOutcome {
    /** The address is now confirmed — blog emails will start arriving. */
    CONFIRMED,

    /** The address has been removed from the list. */
    UNSUBSCRIBED,

    /** The token was rejected: expired, already used, or never valid. */
    INVALID,
}

/**
 * Blog URLs, and the rules for turning a tapped link into one.
 *
 * The blog at `https://interlinedlist.com/blog` is **server-rendered** and the API
 * exposes no public listing or post endpoint (see issue #87), so this app has nothing
 * to render in-app: every blog destination resolves to a web URL that is handed to a
 * Custom Tab. The email list is the exception — it has a real JSON API, so its links
 * are handled in-app (see [subscriptionLinkFor]).
 *
 * Kept as plain JVM code (no `android.net.Uri`) so the matching rules are covered by
 * fast unit tests, matching the `EmailChangeLink` precedent in `:feature:auth`.
 *
 * ## Which links the app claims
 *
 * For *reading* the blog the manifest registers **only** the app's own
 * `interlinedlist://blog…` scheme. The `https://interlinedlist.com/blog…` web URLs are
 * deliberately *not* claimed: with no in-app renderer, claiming them would intercept a
 * browser-bound link only to hand it straight back to a browser — losing the user's own
 * browser session and, when no Custom Tabs provider exists, resolving the plain
 * `ACTION_VIEW` fallback back into this very activity. [webUrlFor] still recognises the
 * web form so in-app call sites may pass either shape, and so the claim can be switched
 * on in the manifest without touching these rules if a real in-app renderer ever lands.
 *
 * The two **email-list** links are claimed in both forms, because the app genuinely
 * handles them: it calls the endpoint itself and shows the result, so nothing is
 * bounced back out to a browser.
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

    /**
     * Path of the double opt-in confirmation link, verified live: the confirmation
     * email points straight at the API route, which answers `307` to
     * `/blog?subscription=confirmed|invalid`.
     */
    const val CONFIRM_PATH = "api/blog/subscribe/confirm"

    /**
     * Path of the human-facing unsubscribe link in the email footer, verified live:
     * answers `307` to `/blog?subscription=unsubscribed|invalid`.
     */
    const val UNSUBSCRIBE_PATH = "api/blog/unsubscribe"

    /** Custom-scheme authority mirroring [CONFIRM_PATH]. */
    const val CONFIRM_HOST = "blog-subscribe-confirm"

    /** Custom-scheme authority mirroring [UNSUBSCRIBE_PATH]. */
    const val UNSUBSCRIBE_HOST = "blog-unsubscribe"

    /** Query parameter carrying the one-time token on both email-list links. */
    const val TOKEN_PARAM = "token"

    /** Query parameter the server reports the outcome with on its redirect target. */
    const val STATUS_PARAM = "subscription"

    private const val STATUS_CONFIRMED = "confirmed"
    private const val STATUS_UNSUBSCRIBED = "unsubscribed"

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
     * Parses [uri] into a [BlogSubscriptionLink], or returns null when it is neither of
     * the two email-list links.
     *
     * Recognised shapes (scheme/host case-insensitive, trailing slash and extra query
     * parameters tolerated):
     * - `https://interlinedlist.com/api/blog/subscribe/confirm?token=…`
     * - `https://interlinedlist.com/api/blog/unsubscribe?token=…`
     * - `interlinedlist://blog-subscribe-confirm?token=…`
     * - `interlinedlist://blog-unsubscribe?token=…`
     *
     * The path must match **exactly**: the `POST /api/blog/subscribe` target, a deeper
     * `/api/blog/subscribe/confirm/anything` path and an `/api/blog/unsubscribed`
     * look-alike all yield null, so the app never claims to handle a URL it does not
     * understand. A missing or blank `token`, by contrast, still parses — see
     * [BlogSubscriptionLink].
     */
    fun subscriptionLinkFor(uri: String?): BlogSubscriptionLink? {
        val trimmed = uri?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val parsed = runCatching { URI(trimmed) }.getOrNull() ?: return null

        val action = parsed.subscriptionActionOrNull() ?: return null
        val token = parsed.rawQuery.queryParam(TOKEN_PARAM)?.trim().orEmpty()
        return BlogSubscriptionLink(action, token)
    }

    /**
     * Reads the outcome the server redirected to.
     *
     * Both endpoints answer `307` with a `Location` of
     * `https://interlinedlist.com/blog?subscription=confirmed|unsubscribed|invalid`, so
     * that parameter — and only that parameter — is what tells the user whether the tap
     * worked. Anything unrecognised is [BlogSubscriptionOutcome.INVALID]: an unexpected
     * redirect must never be reported as a success.
     */
    fun outcomeFor(redirectLocation: String?): BlogSubscriptionOutcome {
        val query = runCatching { URI(redirectLocation?.trim().orEmpty()) }.getOrNull()?.rawQuery
        return when (query.queryParam(STATUS_PARAM)?.trim()?.lowercase()) {
            STATUS_CONFIRMED -> BlogSubscriptionOutcome.CONFIRMED
            STATUS_UNSUBSCRIBED -> BlogSubscriptionOutcome.UNSUBSCRIBED
            else -> BlogSubscriptionOutcome.INVALID
        }
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

    /**
     * The email-list action this URI targets, or null when it is not one of the two
     * links. Web links carry it in the path; custom-scheme links carry it in the
     * authority (`interlinedlist://blog-unsubscribe?token=…`).
     */
    private fun URI.subscriptionActionOrNull(): BlogSubscriptionAction? {
        val scheme = scheme?.lowercase() ?: return null
        val target = when {
            scheme in WEB_SCHEMES -> {
                val host = host?.lowercase()?.removePrefix("www.") ?: return null
                if (host != WEB_HOST) return null
                rawPath.orEmpty().trim('/')
            }
            scheme == APP_SCHEME -> {
                // Tolerate the action appearing as a leading path segment too.
                ((host ?: authority).orEmpty() + rawPath.orEmpty()).trim('/')
            }
            else -> return null
        }
        return when (target.lowercase()) {
            CONFIRM_PATH, CONFIRM_HOST -> BlogSubscriptionAction.CONFIRM
            UNSUBSCRIBE_PATH, UNSUBSCRIBE_HOST -> BlogSubscriptionAction.UNSUBSCRIBE
            else -> null
        }
    }

    /** Splits an already-trimmed path, dropping the empty parts left by `//`. */
    private fun String.splitPath(): List<String> =
        if (isEmpty()) emptyList() else split('/').filter { it.isNotEmpty() }

    /**
     * Reads a single percent-decoded query parameter out of a raw query string.
     *
     * A local copy of the same helper `EmailChangeLink` uses: that one is private to
     * `:feature:auth`, and widening its visibility to share twelve lines would couple
     * the blog to the auth module's API surface.
     */
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
