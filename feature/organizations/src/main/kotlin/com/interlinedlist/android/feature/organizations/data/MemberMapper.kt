package com.interlinedlist.android.feature.organizations.data

import com.interlinedlist.android.feature.organizations.data.remote.dto.MemberDto
import com.interlinedlist.android.feature.organizations.data.remote.dto.MemberUserDto
import com.interlinedlist.android.feature.organizations.domain.MemberCandidate
import com.interlinedlist.android.feature.organizations.domain.OrgMember
import com.interlinedlist.android.feature.organizations.domain.OrgRole

/**
 * DTO → domain mapping for members and candidate users.
 *
 * A member row reaches the client in two shapes: flattened (`userId`/`username`
 * on the row) or with a nested `user` object. This mapper reads the user id,
 * username, display name, and avatar from whichever is present, and normalises the
 * role via [OrgRole.fromApi], so a member is never dropped for a missing field.
 */
object MemberMapper {

    fun fromDto(dto: MemberDto): OrgMember? {
        val userId = dto.userId ?: dto.user?.id ?: dto.id ?: return null
        return OrgMember(
            userId = userId,
            username = dto.username ?: dto.user?.username ?: userId,
            displayName = dto.displayName ?: dto.user?.displayName,
            avatarUrl = dto.resolvedAvatar,
            role = OrgRole.fromApi(dto.role),
            // Absent `active` defaults to true: a listed member is treated as active.
            active = dto.active ?: true,
        )
    }

    fun candidateFromDto(dto: MemberUserDto): MemberCandidate = MemberCandidate(
        userId = dto.id,
        username = dto.username.ifBlank { dto.id },
        displayName = dto.displayName,
        avatarUrl = dto.resolvedAvatar,
    )
}
