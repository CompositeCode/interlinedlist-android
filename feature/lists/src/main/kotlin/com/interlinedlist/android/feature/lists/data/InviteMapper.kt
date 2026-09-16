package com.interlinedlist.android.feature.lists.data

import com.interlinedlist.android.feature.lists.data.remote.dto.ListInviteDto
import com.interlinedlist.android.feature.lists.domain.InviteRole
import com.interlinedlist.android.feature.lists.domain.ListInvite

/**
 * DTO → domain mapping for email invites.
 *
 * The documented create response omits `token` and only returns the landing `url`,
 * so the token is recovered from the URL's last path segment — without it the owner
 * could not revoke an invite they had just sent.
 */
object InviteMapper {

    fun fromDto(dto: ListInviteDto): ListInvite = ListInvite(
        email = dto.email,
        token = dto.token.ifBlank { tokenFromInviteUrl(dto.url) },
        role = InviteRole.fromApi(dto.role),
        expiresAt = dto.expiresAt,
        createdAt = dto.createdAt,
        accepted = dto.accepted || dto.acceptedAt != null,
        revokedAt = dto.revokedAt,
        url = dto.url,
    )

    /** Last path segment of an invite landing URL (`.../lists/invite/<token>`). */
    private fun tokenFromInviteUrl(url: String?): String =
        url?.substringBefore('?')?.substringBefore('#')?.trimEnd('/')?.substringAfterLast('/').orEmpty()
}
