package com.interlinedlist.android.feature.organizations.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The role × action matrix documented at `https://interlinedlist.com/help/organizations`:
 *
 * - "Owner: Full control; can delete the org and manage all members"
 * - "Admin: Can add and remove members and change roles (except owner)"
 * - "Member: Basic access"
 * - "Private: Invite-only; members must be added by an owner or admin"
 * - "You cannot leave the system \"The Public\" organization."
 *
 * One case per role per action, so a future change to the matrix has to be deliberate.
 */
class OrgPermissionsTest {

    private fun org(
        role: OrgRole?,
        isPublic: Boolean = true,
        isSystem: Boolean = false,
    ) = Organization(
        id = "o1",
        name = "Acme",
        description = null,
        avatarUrl = null,
        isPublic = isPublic,
        memberCount = 3,
        role = role,
        updatedAt = null,
        isSystem = isSystem,
    )

    private fun permissionsFor(
        role: OrgRole?,
        isPublic: Boolean = true,
        isSystem: Boolean = false,
    ) = OrgPermissions.of(org(role, isPublic, isSystem))

    // ---- View members -------------------------------------------------------
    // `GET /api/organizations/{id}/members` is members-only (403 otherwise).

    @Test
    fun `owner admin and member may view members but a non-member may not`() {
        assertThat(permissionsFor(OrgRole.OWNER).canViewMembers).isTrue()
        assertThat(permissionsFor(OrgRole.ADMIN).canViewMembers).isTrue()
        assertThat(permissionsFor(OrgRole.MEMBER).canViewMembers).isTrue()
        assertThat(permissionsFor(null).canViewMembers).isFalse()
    }

    // ---- Add a member -------------------------------------------------------

    @Test
    fun `only an owner or admin may add a member`() {
        assertThat(permissionsFor(OrgRole.OWNER).canAddMember).isTrue()
        assertThat(permissionsFor(OrgRole.ADMIN).canAddMember).isTrue()
        assertThat(permissionsFor(OrgRole.MEMBER).canAddMember).isFalse()
        assertThat(permissionsFor(null).canAddMember).isFalse()
    }

    // ---- Change a member's role --------------------------------------------

    @Test
    fun `an owner may change any member's role`() {
        val owner = permissionsFor(OrgRole.OWNER)
        assertThat(owner.canChangeRoleOf(OrgRole.OWNER)).isTrue()
        assertThat(owner.canChangeRoleOf(OrgRole.ADMIN)).isTrue()
        assertThat(owner.canChangeRoleOf(OrgRole.MEMBER)).isTrue()
    }

    @Test
    fun `an admin may change roles except an owner's`() {
        val admin = permissionsFor(OrgRole.ADMIN)
        assertThat(admin.canChangeRoleOf(OrgRole.OWNER)).isFalse()
        assertThat(admin.canChangeRoleOf(OrgRole.ADMIN)).isTrue()
        assertThat(admin.canChangeRoleOf(OrgRole.MEMBER)).isTrue()
    }

    @Test
    fun `a member and a non-member may not change any role`() {
        for (permissions in listOf(permissionsFor(OrgRole.MEMBER), permissionsFor(null))) {
            OrgRole.entries.forEach { target ->
                assertThat(permissions.canChangeRoleOf(target)).isFalse()
            }
        }
    }

    @Test
    fun `an owner may grant owner but an admin may not`() {
        assertThat(permissionsFor(OrgRole.OWNER).assignableRolesFor(OrgRole.MEMBER))
            .containsExactly(OrgRole.MEMBER, OrgRole.ADMIN, OrgRole.OWNER)
        // "change roles (except owner)" — an admin cannot hand out ownership.
        assertThat(permissionsFor(OrgRole.ADMIN).assignableRolesFor(OrgRole.MEMBER))
            .containsExactly(OrgRole.MEMBER, OrgRole.ADMIN)
        assertThat(permissionsFor(OrgRole.ADMIN).assignableRolesFor(OrgRole.OWNER)).isEmpty()
        assertThat(permissionsFor(OrgRole.MEMBER).assignableRolesFor(OrgRole.MEMBER)).isEmpty()
    }

    // ---- Remove a member ----------------------------------------------------

    @Test
    fun `an owner may remove anyone and an admin anyone but an owner`() {
        val owner = permissionsFor(OrgRole.OWNER)
        OrgRole.entries.forEach { assertThat(owner.canRemove(it)).isTrue() }

        val admin = permissionsFor(OrgRole.ADMIN)
        assertThat(admin.canRemove(OrgRole.OWNER)).isFalse()
        assertThat(admin.canRemove(OrgRole.ADMIN)).isTrue()
        assertThat(admin.canRemove(OrgRole.MEMBER)).isTrue()
    }

    @Test
    fun `a member and a non-member may not remove anyone`() {
        for (permissions in listOf(permissionsFor(OrgRole.MEMBER), permissionsFor(null))) {
            OrgRole.entries.forEach { assertThat(permissions.canRemove(it)).isFalse() }
        }
    }

    // ---- Edit the organization ---------------------------------------------

    @Test
    fun `only an owner or admin may edit the organization`() {
        assertThat(permissionsFor(OrgRole.OWNER).canEditOrganization).isTrue()
        assertThat(permissionsFor(OrgRole.ADMIN).canEditOrganization).isTrue()
        assertThat(permissionsFor(OrgRole.MEMBER).canEditOrganization).isFalse()
        assertThat(permissionsFor(null).canEditOrganization).isFalse()
    }

    @Test
    fun `visibility is editable exactly when the organization is`() {
        // Visibility rides on the same PUT, so it follows the same rule.
        OrgRole.entries.plus(null).forEach { role ->
            val permissions = permissionsFor(role)
            assertThat(permissions.canEditVisibility).isEqualTo(permissions.canEditOrganization)
        }
    }

    // ---- Delete the organization -------------------------------------------

    @Test
    fun `only an owner may delete the organization`() {
        assertThat(permissionsFor(OrgRole.OWNER).canDeleteOrganization).isTrue()
        assertThat(permissionsFor(OrgRole.ADMIN).canDeleteOrganization).isFalse()
        assertThat(permissionsFor(OrgRole.MEMBER).canDeleteOrganization).isFalse()
        assertThat(permissionsFor(null).canDeleteOrganization).isFalse()
    }

    // ---- LinkedIn company pages ---------------------------------------------
    // "When an organization's owners or admins connect a shared LinkedIn
    // credential…" — and the server agrees: a member PUTting an assignment or
    // POSTing a page sync gets 403 {"error":"Admin or owner required"} (live).

    @Test
    fun `only an owner or admin may manage the LinkedIn connection`() {
        assertThat(permissionsFor(OrgRole.OWNER).canManageLinkedIn).isTrue()
        assertThat(permissionsFor(OrgRole.ADMIN).canManageLinkedIn).isTrue()
        assertThat(permissionsFor(OrgRole.MEMBER).canManageLinkedIn).isFalse()
        assertThat(permissionsFor(null).canManageLinkedIn).isFalse()
    }

    @Test
    fun `a system organization's LinkedIn connection follows the same role rule`() {
        // Unlike leaving or deleting, nothing about the credential is special-cased
        // for a built-in organization.
        assertThat(permissionsFor(OrgRole.OWNER, isSystem = true).canManageLinkedIn).isTrue()
        assertThat(permissionsFor(OrgRole.MEMBER, isSystem = true).canManageLinkedIn).isFalse()
    }

    // ---- Join / leave -------------------------------------------------------

    @Test
    fun `only a non-member of a public organization may join`() {
        assertThat(permissionsFor(null, isPublic = true).canJoin).isTrue()
        assertThat(permissionsFor(null, isPublic = false).canJoin).isFalse()
        OrgRole.entries.forEach { assertThat(permissionsFor(it).canJoin).isFalse() }
    }

    @Test
    fun `any member may leave but a non-member has nothing to leave`() {
        OrgRole.entries.forEach { assertThat(permissionsFor(it).canLeave).isTrue() }
        assertThat(permissionsFor(null).canLeave).isFalse()
    }

    // ---- System organizations ----------------------------------------------

    @Test
    fun `a system organization cannot be left or deleted by anyone`() {
        // "You cannot leave the system \"The Public\" organization."
        OrgRole.entries.forEach { role ->
            val permissions = permissionsFor(role, isSystem = true)
            assertThat(permissions.canLeave).isFalse()
            assertThat(permissions.canDeleteOrganization).isFalse()
        }
    }

    @Test
    fun `an absent organization permits nothing`() {
        val none = OrgPermissions.of(null)
        assertThat(none.canViewMembers).isFalse()
        assertThat(none.canAddMember).isFalse()
        assertThat(none.canEditOrganization).isFalse()
        assertThat(none.canDeleteOrganization).isFalse()
        assertThat(none.canLeave).isFalse()
        assertThat(none.canJoin).isFalse()
        assertThat(none.canManageLinkedIn).isFalse()
    }
}
