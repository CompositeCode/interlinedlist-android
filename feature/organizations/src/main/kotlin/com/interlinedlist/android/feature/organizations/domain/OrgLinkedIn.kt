package com.interlinedlist.android.feature.organizations.domain

/**
 * The organization's shared LinkedIn credential, the company pages discovered for
 * it, and which member may post to which page.
 *
 * Captured live from `GET /api/organizations/{id}/linkedin/status`, which answers
 * `{"credential":null,"role":"member"}` for an organization that has never
 * connected LinkedIn — **the normal case**, not an error. [NOT_CONNECTED] models
 * exactly that, so the UI can say "no LinkedIn credential" rather than "something
 * went wrong".
 *
 * The *connected* payload was not observable (no account reachable from here has
 * an org credential), so the wire model behind this is deliberately tolerant; see
 * `OrgLinkedInDtos`.
 */
data class OrgLinkedInStatus(
    /** True once the organization holds a LinkedIn credential. */
    val connected: Boolean,
    /** When the stored credential expires, if the API reports it. */
    val expiresAt: String? = null,
    /** Company pages discovered for the credential; empty until a sync finds any. */
    val pages: List<OrgLinkedInPage> = emptyList(),
    /**
     * Which page each member posts to, keyed by user id. The API assigns at most
     * one page per member: `PUT …/linkedin/assignments` takes a single
     * `{ userId, pageId }` pair and clears the member's assignment when `pageId`
     * is absent (verified live).
     */
    val assignments: Map<String, String> = emptyMap(),
) {

    /** The page assigned to [userId], or null when they have none (or it is unknown). */
    fun pageFor(userId: String): OrgLinkedInPage? =
        assignments[userId]?.let { pageId -> pages.firstOrNull { it.id == pageId } }

    companion object {
        /** An organization with no LinkedIn credential — the ordinary starting state. */
        val NOT_CONNECTED = OrgLinkedInStatus(connected = false)
    }
}

/**
 * One LinkedIn company page bound to the organization (an `OrgLinkedInPage`
 * record). [id] is the InterlinedList record id — the value `…/linkedin/assignments`
 * expects as `pageId` — while [linkedInPageId] is LinkedIn's own identifier.
 */
data class OrgLinkedInPage(
    val id: String,
    val linkedInPageId: String? = null,
    val name: String,
    val logoUrl: String? = null,
    val lastSyncedAt: String? = null,
) {
    /** Best label for a row: the page name, falling back to a placeholder. */
    val displayName: String get() = name.ifBlank { "LinkedIn page" }
}
