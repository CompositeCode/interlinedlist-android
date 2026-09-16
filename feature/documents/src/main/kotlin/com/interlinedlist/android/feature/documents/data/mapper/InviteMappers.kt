package com.interlinedlist.android.feature.documents.data.mapper

import com.interlinedlist.android.feature.documents.data.remote.dto.DocumentInviteDto
import com.interlinedlist.android.feature.documents.domain.DocumentInvite
import com.interlinedlist.android.feature.documents.domain.InviteRole

/**
 * Maps an invite wire model into the domain [DocumentInvite].
 *
 * The documented create response omits `token` and only returns the landing `url`,
 * so the token is recovered from the URL's last path segment — without it the owner
 * could not revoke an invite they had just sent.
 */
fun DocumentInviteDto.toDomain(): DocumentInvite = DocumentInvite(
    email = email,
    token = token.ifBlank { tokenFromInviteUrl(url) },
    role = InviteRole.fromApi(role),
    expiresAt = expiresAt,
    createdAt = createdAt,
    accepted = accepted || acceptedAt != null,
    revokedAt = revokedAt,
    url = url,
)

/** Last path segment of an invite landing URL (`.../documents/invite/<token>`). */
private fun tokenFromInviteUrl(url: String?): String =
    url?.substringBefore('?')?.substringBefore('#')?.trimEnd('/')?.substringAfterLast('/').orEmpty()
