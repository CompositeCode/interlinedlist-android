package com.interlinedlist.android.feature.documents.domain

import java.time.Instant

/**
 * An email invite to a document: a role granted to an *email address* rather than
 * to an account, so the invitee need not have signed up yet and the document stays
 * private throughout. Unlike a [ShareLink] (a bearer capability), the [token] only
 * becomes access once someone signed in with that verified address claims it.
 *
 * Timestamps stay as the API's raw ISO-8601 strings (the module's convention — see
 * [ShareLink]); [statusAt] derives the displayed status from them.
 */
data class DocumentInvite(
    val email: String,
    val token: String,
    val role: InviteRole,
    /** ISO-8601 instant after which the invite stops resolving; null == no expiry. */
    val expiresAt: String?,
    val createdAt: String?,
    /** True once the invitee has claimed the invite. */
    val accepted: Boolean,
    /** ISO-8601 instant the owner revoked the invite, when the server reports one. */
    val revokedAt: String?,
    /** The landing URL the server generated, when it returned one. */
    val url: String?,
) {
    /**
     * The status to show against this invite. Revocation and acceptance are terminal
     * facts the server reports; expiry is a function of the clock, so [now] is a
     * parameter to keep rendering deterministic in tests.
     */
    fun statusAt(now: Instant = Instant.now()): InviteStatus = when {
        revokedAt != null -> InviteStatus.REVOKED
        accepted -> InviteStatus.ACCEPTED
        hasExpiredAt(now) -> InviteStatus.EXPIRED
        else -> InviteStatus.PENDING
    }

    /** True when [expiresAt] parses and is at or before [now]. Unparseable == not expired. */
    private fun hasExpiredAt(now: Instant): Boolean {
        val expiry = expiresAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return false
        return !expiry.isAfter(now)
    }

    /** The landing address for this invite, falling back to the canonical path. */
    fun inviteUrl(baseUrl: String = ShareLink.INTERLINEDLIST_BASE_URL): String =
        url?.takeIf { it.isNotBlank() } ?: "${baseUrl.trimEnd('/')}/documents/invite/$token"
}

/**
 * Displayed state of an invite. The API reports only `accepted` (plus optional
 * `expiresAt` / `revokedAt`), so the four states the web app shows are derived
 * client-side — see [DocumentInvite.statusAt].
 */
enum class InviteStatus(val label: String) {
    PENDING("Pending"),
    ACCEPTED("Accepted"),
    EXPIRED("Expired"),
    REVOKED("Revoked"),
}

/**
 * Access an email invite grants. The invite endpoints take the server's sharing
 * vocabulary (`watcher` / `collaborator` / `manager`) while the UI uses the web
 * app's labels (Viewer / Editor / Admin). Unknown values map to [VIEWER] so an
 * invite is never over-privileged.
 */
enum class InviteRole(val apiValue: String, val label: String) {
    VIEWER("watcher", "Viewer"),
    EDITOR("collaborator", "Editor"),
    ADMIN("manager", "Admin");

    companion object {
        /** Maps an API role string (case-insensitive) to an [InviteRole]. */
        fun fromApi(raw: String?): InviteRole = when (raw?.trim()?.lowercase()) {
            "collaborator", "editor", "edit", "write" -> EDITOR
            "manager", "admin", "owner" -> ADMIN
            else -> VIEWER
        }
    }
}

/**
 * Syntactic validation + normalisation of an invited address, matching what the
 * server does (it stores the address lowercased/trimmed and 400s on an invalid
 * one). Shared by the UI — to keep Send disabled and show an inline error — and
 * by the repository, which refuses to issue a request for an invalid address.
 */
object InviteEmail {

    /** The message shown when [isValid] rejects an address. */
    const val INVALID_MESSAGE = "Enter a valid email address."

    private val PATTERN = Regex("^[^\\s@]+@[^\\s@.]+(\\.[^\\s@.]+)+$")

    /** Trims and lowercases, as the server stores it. */
    fun normalize(raw: String): String = raw.trim().lowercase()

    /** True when [raw] is syntactically a usable address. */
    fun isValid(raw: String): Boolean = normalize(raw).let { it.length <= MAX_LENGTH && PATTERN.matches(it) }

    private const val MAX_LENGTH = 254
}
