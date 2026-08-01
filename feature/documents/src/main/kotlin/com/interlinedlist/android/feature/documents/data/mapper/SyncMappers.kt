package com.interlinedlist.android.feature.documents.data.mapper

import com.interlinedlist.android.feature.documents.data.remote.dto.CollaboratorDto
import com.interlinedlist.android.feature.documents.data.remote.dto.PresenceDto
import com.interlinedlist.android.feature.documents.data.remote.dto.SyncDocumentDto
import com.interlinedlist.android.feature.documents.data.remote.dto.SyncFolderDto
import com.interlinedlist.android.feature.documents.data.remote.dto.UserSummaryDto
import com.interlinedlist.android.feature.documents.domain.Collaborator
import com.interlinedlist.android.feature.documents.domain.CollaboratorCandidate
import com.interlinedlist.android.feature.documents.domain.CollaboratorRole
import com.interlinedlist.android.feature.documents.domain.Document
import com.interlinedlist.android.feature.documents.domain.DocumentFolder
import com.interlinedlist.android.feature.documents.domain.Presence

/** Maps a delta-sync document row into the domain [Document] (carries the version). */
fun SyncDocumentDto.toDomain(): Document = Document(
    id = id,
    title = title?.takeIf { it.isNotBlank() } ?: "Untitled",
    content = content,
    snippet = Document.snippetFrom(content),
    folderId = folderId,
    folderName = null,
    isPublic = isPublic,
    updatedAt = updatedAt ?: createdAt,
    version = version,
)

/** Maps a delta-sync folder row into the domain [DocumentFolder]. */
fun SyncFolderDto.toDomain(): DocumentFolder = DocumentFolder(
    id = id,
    name = name?.takeIf { it.isNotBlank() } ?: "Untitled folder",
    parentId = parentId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/** Maps a collaborator wire row into the domain [Collaborator]. */
fun CollaboratorDto.toDomain(): Collaborator = Collaborator(
    userId = resolvedUserId,
    role = CollaboratorRole.fromApi(role),
    displayName = resolvedDisplayName,
    username = resolvedUsername,
    email = resolvedEmail,
    avatarUrl = resolvedAvatar,
)

/** Maps a searchable user into an invite [CollaboratorCandidate]. */
fun UserSummaryDto.toCandidate(): CollaboratorCandidate = CollaboratorCandidate(
    userId = id,
    username = username,
    displayName = displayName,
    email = email,
    avatarUrl = avatar,
)

/** Maps a presence heartbeat row into the domain [Presence]. */
fun PresenceDto.toDomain(): Presence = Presence(
    userId = resolvedUserId,
    displayName = resolvedDisplayName,
    username = resolvedUsername,
    avatarUrl = resolvedAvatar,
)
