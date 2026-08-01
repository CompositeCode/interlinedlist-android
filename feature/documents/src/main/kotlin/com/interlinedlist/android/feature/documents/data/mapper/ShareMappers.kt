package com.interlinedlist.android.feature.documents.data.mapper

import com.interlinedlist.android.feature.documents.data.remote.dto.ShareLinkDto
import com.interlinedlist.android.feature.documents.data.remote.dto.SharedDocumentResponse
import com.interlinedlist.android.feature.documents.domain.ShareLink
import com.interlinedlist.android.feature.documents.domain.ShareRole
import com.interlinedlist.android.feature.documents.domain.SharedDocument

/** Maps a share-link wire model into the domain [ShareLink]. */
fun ShareLinkDto.toDomain(): ShareLink = ShareLink(
    id = id,
    token = token,
    role = ShareRole.fromApi(role),
    expiresAt = expiresAt,
    revokedAt = revokedAt,
    createdAt = createdAt,
)

/** Maps a resolved-link response into the domain [SharedDocument] preview. */
fun SharedDocumentResponse.toSharedDocument(token: String): SharedDocument {
    val doc = documentOrSelf
    val owner = user?.let { it.displayName?.takeIf(String::isNotBlank) ?: it.username.takeIf(String::isNotBlank) }
    return SharedDocument(
        token = token,
        documentId = doc?.id.orEmpty(),
        title = doc?.title?.takeIf(String::isNotBlank) ?: "Untitled",
        content = doc?.content,
        ownerName = owner,
        role = ShareRole.fromApi(role),
    )
}
