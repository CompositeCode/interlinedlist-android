package com.interlinedlist.android.feature.organizations.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Wire models for an organization's shared LinkedIn credential.
 *
 * **Verified live** (subscriber token, 2026-09-16):
 * - `GET /api/organizations/{id}/linkedin/status` → `{"credential":null,"role":"owner"}`
 *   for an organization with no credential. The help centre's documented
 *   `{"connected":true,"expiresAt":"…"}` shape was never returned, so both forms
 *   are accepted: `connected` if the server ever sends it, otherwise the presence
 *   of `credential`.
 * - `GET …/linkedin/assignments` and `GET …/linkedin/sync-pages` answer **405** —
 *   the help centre's table lists GETs that do not exist, so the page list and the
 *   assignments can only arrive on the status payload.
 *
 * **Not observable**: no reachable account has an organization credential, so the
 * *connected* payload — the credential object, the discovered pages and the
 * assignment map — is modelled defensively. Every field is optional and each is
 * read under the names the rest of the API already uses for a LinkedIn page
 * (`/help/api/linkedin-integration` returns `id`/`pageId`, `linkedInPageId`,
 * `pageName`/`label`, `pageLogoUrl`/`logoUrl`, `lastSyncedAt`).
 */
@Serializable
data class OrgLinkedInStatusResponse(
    val credential: OrgLinkedInCredentialDto? = null,
    @Serializable(with = FlexibleBooleanSerializer::class)
    val connected: Boolean? = null,
    val expiresAt: String? = null,
    /** The caller's role in the organization; the server includes it on status. */
    val role: String? = null,
    val pages: List<OrgLinkedInPageDto>? = null,
    val assignments: List<OrgLinkedInAssignmentDto>? = null,
)

/** The stored credential. Its absence (`null`) is how "not connected" is reported. */
@Serializable
data class OrgLinkedInCredentialDto(
    val expiresAt: String? = null,
    val connectedAt: String? = null,
    val pages: List<OrgLinkedInPageDto>? = null,
    val assignments: List<OrgLinkedInAssignmentDto>? = null,
)

/** One discovered company page (`OrgLinkedInPage`), under either field naming. */
@Serializable
data class OrgLinkedInPageDto(
    val id: String? = null,
    val pageId: String? = null,
    val linkedInPageId: String? = null,
    val pageName: String? = null,
    val name: String? = null,
    val label: String? = null,
    val pageLogoUrl: String? = null,
    val logoUrl: String? = null,
    val lastSyncedAt: String? = null,
    /** Some payloads may carry the assignment on the page rather than separately. */
    val assignedUserId: String? = null,
) {
    /** The InterlinedList record id — what `…/assignments` expects as `pageId`. */
    val resolvedId: String? get() = id ?: pageId
    val resolvedName: String get() = pageName ?: name ?: label ?: ""
    val resolvedLogo: String? get() = pageLogoUrl ?: logoUrl
}

/** A member → page assignment as reported by the status payload. */
@Serializable
data class OrgLinkedInAssignmentDto(
    val userId: String? = null,
    val pageId: String? = null,
)

/**
 * Body for `PUT /api/organizations/{id}/linkedin/assignments`.
 *
 * Verified live: the endpoint takes **one** assignment, not a map —
 * `{}` answers `400 {"error":"userId required","code":"bad_request"}`, a `userId`
 * with no `pageId` answers `200 {"assigned":false}` (i.e. it clears that member's
 * assignment), an unknown page answers
 * `404 {"error":"Page not found in this organization"}` and a non-member answers
 * `400 {"error":"User is not a member of this organization"}`.
 *
 * The shared `Json` sets `explicitNulls = false`, so a null [pageId] is omitted —
 * which is exactly the "unassign" form the server accepts.
 */
@Serializable
data class LinkedInAssignmentRequest(
    val userId: String,
    val pageId: String? = null,
)

/** Response of the assignment PUT: `{"assigned": true|false}` (verified live). */
@Serializable
data class LinkedInAssignmentResponse(
    @Serializable(with = FlexibleBooleanSerializer::class)
    val assigned: Boolean? = null,
)
