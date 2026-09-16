package com.interlinedlist.android.feature.organizations.domain

/**
 * What the signed-in user may do in one organization, derived from the role the API
 * reports for them. The rules are taken verbatim from the help centre
 * (`https://interlinedlist.com/help/organizations`) — nothing here is inferred:
 *
 * - "Owner: Full control; can delete the org and manage all members"
 * - "Admin: Can add and remove members and change roles (except owner)"
 * - "Member: Basic access"
 * - "Private: Invite-only; members must be added by an owner or admin"
 * - "Public: Anyone can see and join"
 * - "You cannot leave the system \"The Public\" organization."
 *
 * Membership itself is signalled by the presence of a role: the API omits `role` for
 * organizations the caller does not belong to, and reports `userRole: null` on the
 * detail endpoint.
 *
 * This gates the UI only. Hiding an action is a usability improvement, not a
 * security boundary — every mutation still goes to the server, which stays
 * authoritative and whose rejections are surfaced to the user.
 */
data class OrgPermissions(
    /** The signed-in user's role, or `null` when they are not a member. */
    val viewerRole: OrgRole?,
    val isPublic: Boolean,
    /** A built-in organization such as "The Public"; it cannot be left or deleted. */
    val isSystem: Boolean,
) {

    val isMember: Boolean get() = viewerRole != null

    /**
     * Owners and admins administer the organization; the help centre addresses both
     * as "an owner or admin" everywhere it describes management.
     */
    private val administers: Boolean
        get() = viewerRole == OrgRole.OWNER || viewerRole == OrgRole.ADMIN

    /** `GET /api/organizations/{id}/members` is members-only (403 otherwise). */
    val canViewMembers: Boolean get() = isMember

    val canAddMember: Boolean get() = administers

    /** Name, description and avatar — "an owner or admin" manages the organization. */
    val canEditOrganization: Boolean get() = administers

    /** Visibility rides on the same `PUT`, so it follows the edit rule exactly. */
    val canEditVisibility: Boolean get() = canEditOrganization

    /** "Owner: … can delete the org" — and a system organization is never deletable. */
    val canDeleteOrganization: Boolean get() = viewerRole == OrgRole.OWNER && !isSystem

    /** Any member may leave, except from a system organization. */
    val canLeave: Boolean get() = isMember && !isSystem

    /** "Public: Anyone can see and join"; joining a private org answers 403. */
    val canJoin: Boolean get() = !isMember && isPublic

    /** True when any organization-level action is available, so the menu is worth showing. */
    val hasAnyOrganizationAction: Boolean
        get() = canEditOrganization || canDeleteOrganization || canLeave

    /** An admin may not act on an owner: "change roles (except owner)". */
    private fun canManage(targetRole: OrgRole): Boolean = when (viewerRole) {
        OrgRole.OWNER -> true
        OrgRole.ADMIN -> targetRole != OrgRole.OWNER
        else -> false
    }

    /** Whether the role of a member currently holding [targetRole] may be changed. */
    fun canChangeRoleOf(targetRole: OrgRole): Boolean = canManage(targetRole)

    /** Whether a member currently holding [targetRole] may be removed. */
    fun canRemove(targetRole: OrgRole): Boolean = canManage(targetRole)

    /**
     * The roles this viewer may assign to a member currently holding [targetRole],
     * least- to most-privileged. Empty when the member is out of reach. An admin
     * cannot hand out ownership, so `owner` is withheld from them.
     */
    fun assignableRolesFor(targetRole: OrgRole): List<OrgRole> = when {
        !canManage(targetRole) -> emptyList()
        viewerRole == OrgRole.OWNER -> OrgRole.entries
        else -> OrgRole.entries.filterNot { it == OrgRole.OWNER }
    }

    companion object {
        /** Permits nothing — the state before an organization has loaded. */
        val NONE = OrgPermissions(viewerRole = null, isPublic = false, isSystem = false)

        fun of(organization: Organization?): OrgPermissions = organization?.let {
            OrgPermissions(viewerRole = it.role, isPublic = it.isPublic, isSystem = it.isSystem)
        } ?: NONE
    }
}
