package com.interlinedlist.android.feature.organizations.data

import com.interlinedlist.android.feature.organizations.data.local.CachedOrganizationEntity
import com.interlinedlist.android.feature.organizations.data.remote.dto.OrganizationDto
import com.interlinedlist.android.feature.organizations.domain.OrgRole
import com.interlinedlist.android.feature.organizations.domain.Organization

/** DTO/entity ↔ domain mapping for organizations. */
object OrganizationMapper {

    fun fromDto(dto: OrganizationDto): Organization = Organization(
        id = dto.id,
        name = dto.name,
        description = dto.description,
        avatarUrl = dto.resolvedAvatar,
        isPublic = dto.resolvedPublic,
        memberCount = dto.resolvedMemberCount,
        role = dto.role?.let(OrgRole::fromApi),
        updatedAt = dto.updatedAt,
    )

    fun toEntity(org: Organization): CachedOrganizationEntity = CachedOrganizationEntity(
        id = org.id,
        name = org.name,
        description = org.description,
        avatarUrl = org.avatarUrl,
        isPublic = org.isPublic,
        memberCount = org.memberCount,
        role = org.role?.apiValue,
        updatedAt = org.updatedAt,
    )

    fun fromEntity(entity: CachedOrganizationEntity): Organization = Organization(
        id = entity.id,
        name = entity.name,
        description = entity.description,
        avatarUrl = entity.avatarUrl,
        isPublic = entity.isPublic,
        memberCount = entity.memberCount,
        role = entity.role?.let(OrgRole::fromApi),
        updatedAt = entity.updatedAt,
    )
}
