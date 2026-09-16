package com.interlinedlist.android.feature.auth.nav

import java.net.URI
import java.net.URLDecoder

/** Which half of the email-change flow an emailed link completes. */
enum class EmailChangeAction {
    /** Confirm the new address — the link mailed to the *new* inbox. */
    VERIFY,

    /** Revert the change — the link mailed to the *previous* inbox. */
    UNDO,
}

/**
 * A parsed email-change deep link: which [action] the tapped link performs and the
 * one-time [token] it carries.
 *
 * Kept as plain JVM code (no `android.net.Uri`) so the parsing rules are covered by
 * fast unit tests — this is the entry point for a *security* action reached from an
 * email, so silently mis-parsing it is not acceptable.
 */
data class EmailChangeLink(
    val action: EmailChangeAction,
    val token: String,
) {
    companion object {

        /** Query parameter carrying the one-time token on both links. */
        const val TOKEN_PARAM = "token"

        /** Web path for the "confirm the new address" link. */
        const val VERIFY_PATH = "verify-email-change"

        /** Web path for the "this wasn't me — undo it" link. */
        const val UNDO_PATH = "undo-email-change"

        /** Host of the web links; also matched with a `www.` prefix. */
        const val WEB_HOST = "interlinedlist.com"

        /** Custom scheme the app registers for the same two links. */
        const val APP_SCHEME = "interlinedlist"

        private val WEB_SCHEMES = setOf("https", "http")

        /**
         * Parses [uri] into an [EmailChangeLink], or returns null when it is not one
         * of the two email-change links or carries no usable token.
         *
         * Recognised shapes (scheme/host case-insensitive, trailing slash and extra
         * query parameters tolerated):
         * - `https://interlinedlist.com/verify-email-change?token=…`
         * - `https://interlinedlist.com/undo-email-change?token=…`
         * - `interlinedlist://verify-email-change?token=…`
         * - `interlinedlist://undo-email-change?token=…`
         *
         * Anything malformed — a missing or blank token, a foreign host, an unrelated
         * path, or a string that is not a URI at all — yields null rather than an
         * exception or a half-populated link.
         */
        fun parse(uri: String?): EmailChangeLink? {
            val trimmed = uri?.trim().orEmpty()
            if (trimmed.isEmpty()) return null
            val parsed = runCatching { URI(trimmed) }.getOrNull() ?: return null

            val action = parsed.actionOrNull() ?: return null
            val token = parsed.rawQuery.queryParam(TOKEN_PARAM)?.takeIf { it.isNotBlank() } ?: return null
            return EmailChangeLink(action, token)
        }

        /**
         * The action this URI targets, or null when it is not an email-change link.
         * Web links carry the action in the path; custom-scheme links carry it in the
         * authority (`interlinedlist://verify-email-change`).
         */
        private fun URI.actionOrNull(): EmailChangeAction? {
            val scheme = scheme?.lowercase() ?: return null
            val target = when {
                scheme in WEB_SCHEMES -> {
                    val host = host?.lowercase()?.removePrefix("www.") ?: return null
                    if (host != WEB_HOST) return null
                    path.orEmpty().trim('/')
                }
                scheme == APP_SCHEME -> {
                    // `interlinedlist://verify-email-change?token=…` — the action sits in
                    // the authority; tolerate it appearing as a leading path segment too.
                    val authority = host ?: authority
                    (authority.orEmpty() + path.orEmpty()).trim('/')
                }
                else -> return null
            }
            return when (target.lowercase()) {
                VERIFY_PATH -> EmailChangeAction.VERIFY
                UNDO_PATH -> EmailChangeAction.UNDO
                else -> null
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
}
