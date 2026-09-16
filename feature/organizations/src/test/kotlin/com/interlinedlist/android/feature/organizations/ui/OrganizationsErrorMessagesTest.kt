package com.interlinedlist.android.feature.organizations.ui

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.AppError
import org.junit.Test

/**
 * The join/leave failures the live API actually returns, turned into explanations.
 * Messages captured against interlinedlist.com: 409 "User is already a member of
 * this organization", 403 for a private org, and 400 "Cannot remove the last owner".
 */
class OrganizationsErrorMessagesTest {

    @Test
    fun `last-owner rejection is explained, not echoed as a generic error`() {
        val error = AppError.Unknown("Cannot remove the last owner")

        assertThat(error.isLastOwnerRejection).isTrue()
        assertThat(error.toLeaveMessage()).isEqualTo(LAST_OWNER_EXPLANATION)
        assertThat(error.toLeaveMessage()).contains("Make another member an owner")
        // The generic mapping would have leaked the raw API string.
        assertThat(error.toLeaveMessage()).isNotEqualTo(error.toUserMessage())
    }

    @Test
    fun `other leave failures fall back to the shared mapping`() {
        assertThat(AppError.Network(null).toLeaveMessage())
            .isEqualTo(AppError.Network(null).toUserMessage())
    }

    @Test
    fun `joining an org you already belong to reads as a conflict`() {
        val error = AppError.Conflict("User is already a member of this organization")

        assertThat(error.toJoinMessage()).isEqualTo("You're already a member of this organization.")
    }

    @Test
    fun `joining a private org explains it cannot be self-joined`() {
        val error = AppError.Forbidden("Organization is private")

        assertThat(error.toJoinMessage()).contains("private")
        assertThat(error.toJoinMessage()).contains("Ask an owner or admin")
    }

    @Test
    fun `joining a missing org reports it as not found`() {
        assertThat(AppError.NotFound("Organization not found").toJoinMessage())
            .isEqualTo("That organization could not be found.")
    }

    @Test
    fun `a last-owner demote rejection is explained in role-change terms`() {
        // Live 400: {"error":"Cannot demote the last owner","code":"bad_request"}
        val error = AppError.Unknown("Cannot demote the last owner")

        assertThat(error.isLastOwnerRejection).isTrue()
        assertThat(error.toRoleChangeMessage()).isEqualTo(LAST_OWNER_DEMOTE_EXPLANATION)
        assertThat(error.toRoleChangeMessage()).contains("Make someone else an owner first")
        assertThat(error.toRoleChangeMessage()).isNotEqualTo(error.toUserMessage())
    }

    @Test
    fun `a last-owner remove rejection is explained in removal terms`() {
        // Live 400: {"error":"Cannot remove the last owner","code":"bad_request"}
        val error = AppError.Unknown("Cannot remove the last owner")

        assertThat(error.toRemoveMemberMessage()).isEqualTo(LAST_OWNER_REMOVE_EXPLANATION)
        assertThat(error.toRemoveMemberMessage()).contains("remove them")
    }

    @Test
    fun `other member-management failures fall back to the shared mapping`() {
        val forbidden = AppError.Forbidden("Nope")

        assertThat(forbidden.toRoleChangeMessage()).isEqualTo(forbidden.toUserMessage())
        assertThat(forbidden.toRemoveMemberMessage()).isEqualTo(forbidden.toUserMessage())
    }
}
