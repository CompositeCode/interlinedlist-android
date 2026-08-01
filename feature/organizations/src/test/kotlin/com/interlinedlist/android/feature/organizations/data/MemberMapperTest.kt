package com.interlinedlist.android.feature.organizations.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.feature.organizations.data.remote.dto.MemberDto
import com.interlinedlist.android.feature.organizations.data.remote.dto.MemberUserDto
import com.interlinedlist.android.feature.organizations.domain.OrgRole
import org.junit.Test

/**
 * Member rows arrive flattened or nested and with varied role strings; the mapper
 * tolerates both shapes and normalises the role so a member is never dropped.
 */
class MemberMapperTest {

    @Test
    fun `maps a flattened member row`() {
        val member = MemberMapper.fromDto(
            MemberDto(userId = "u1", username = "ada", displayName = "Ada", role = "admin", active = true),
        )

        assertThat(member).isNotNull()
        assertThat(member!!.userId).isEqualTo("u1")
        assertThat(member.username).isEqualTo("ada")
        assertThat(member.label).isEqualTo("Ada")
        assertThat(member.role).isEqualTo(OrgRole.ADMIN)
        assertThat(member.active).isTrue()
    }

    @Test
    fun `maps a nested user object and defaults an unknown role to member`() {
        val member = MemberMapper.fromDto(
            MemberDto(
                role = "wizard",
                user = MemberUserDto(id = "u2", username = "grace", displayName = null),
            ),
        )

        assertThat(member!!.userId).isEqualTo("u2")
        assertThat(member.username).isEqualTo("grace")
        // No display name → the row labels by username.
        assertThat(member.label).isEqualTo("grace")
        assertThat(member.role).isEqualTo(OrgRole.MEMBER)
        // Absent active flag defaults to true (a listed member is active).
        assertThat(member.active).isTrue()
    }

    @Test
    fun `returns null when no user id can be resolved`() {
        assertThat(MemberMapper.fromDto(MemberDto(role = "member"))).isNull()
    }

    @Test
    fun `maps a candidate user`() {
        val candidate = MemberMapper.candidateFromDto(
            MemberUserDto(id = "u3", username = "linus", displayName = "Linus"),
        )

        assertThat(candidate.userId).isEqualTo("u3")
        assertThat(candidate.label).isEqualTo("Linus")
    }
}
