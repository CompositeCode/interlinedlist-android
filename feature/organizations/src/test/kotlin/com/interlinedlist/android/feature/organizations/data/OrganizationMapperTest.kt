package com.interlinedlist.android.feature.organizations.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.feature.organizations.data.remote.dto.OrganizationDto
import com.interlinedlist.android.feature.organizations.domain.OrgRole
import com.interlinedlist.android.feature.organizations.domain.Organization
import org.junit.Test

/**
 * Organizations arrive with counts under several field names and an optional role;
 * the mapper resolves those, and entity round-tripping preserves the domain shape.
 */
class OrganizationMapperTest {

    @Test
    fun `maps a dto resolving avatar public and member count`() {
        val org = OrganizationMapper.fromDto(
            OrganizationDto(
                id = "o1",
                name = "Acme",
                description = "Makers",
                avatar = "https://img/acme.png",
                isPublic = true,
                membersCount = 7,
                role = "admin",
            ),
        )

        assertThat(org.id).isEqualTo("o1")
        assertThat(org.avatarUrl).isEqualTo("https://img/acme.png")
        assertThat(org.isPublic).isTrue()
        assertThat(org.memberCount).isEqualTo(7)
        assertThat(org.role).isEqualTo(OrgRole.ADMIN)
    }

    @Test
    fun `defaults an absent public flag to private and a missing count to zero`() {
        val org = OrganizationMapper.fromDto(OrganizationDto(id = "o2", name = "Nameless"))

        assertThat(org.isPublic).isFalse()
        assertThat(org.memberCount).isEqualTo(0)
        assertThat(org.role).isNull()
    }

    @Test
    fun `entity round-trip preserves the organization`() {
        val org = Organization(
            id = "o3",
            name = "Round Trip",
            description = "desc",
            avatarUrl = "a",
            isPublic = true,
            memberCount = 3,
            role = OrgRole.OWNER,
            updatedAt = "2026-01-01",
        )

        val restored = OrganizationMapper.fromEntity(OrganizationMapper.toEntity(org))

        assertThat(restored).isEqualTo(org)
    }
}
