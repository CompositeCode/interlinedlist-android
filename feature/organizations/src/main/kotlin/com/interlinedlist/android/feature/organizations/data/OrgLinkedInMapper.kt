package com.interlinedlist.android.feature.organizations.data

import com.interlinedlist.android.feature.organizations.data.remote.dto.OrgLinkedInPageDto
import com.interlinedlist.android.feature.organizations.data.remote.dto.OrgLinkedInStatusResponse
import com.interlinedlist.android.feature.organizations.domain.OrgLinkedInPage
import com.interlinedlist.android.feature.organizations.domain.OrgLinkedInStatus

/**
 * DTO → domain mapping for the organization LinkedIn status.
 *
 * "Not connected" is the ordinary answer (`{"credential":null}` — verified live),
 * so it maps to [OrgLinkedInStatus.NOT_CONNECTED] rather than being treated as a
 * missing payload. The connected payload could not be observed, so pages and
 * assignments are read from either the top level or from inside `credential`, and
 * an assignment is accepted either as its own `{userId,pageId}` row or as an
 * `assignedUserId` on the page.
 */
object OrgLinkedInMapper {

    fun fromDto(dto: OrgLinkedInStatusResponse): OrgLinkedInStatus {
        val credential = dto.credential
        val connected = dto.connected ?: (credential != null)
        val pageDtos = credential?.pages ?: dto.pages.orEmpty()
        val pages = pageDtos.mapNotNull(::pageFromDto)
        val assignmentDtos = credential?.assignments ?: dto.assignments.orEmpty()

        val assignments = buildMap {
            pageDtos.forEach { page ->
                val pageId = page.resolvedId ?: return@forEach
                page.assignedUserId?.let { put(it, pageId) }
            }
            assignmentDtos.forEach { assignment ->
                val userId = assignment.userId ?: return@forEach
                val pageId = assignment.pageId ?: return@forEach
                put(userId, pageId)
            }
        }

        return OrgLinkedInStatus(
            connected = connected,
            expiresAt = credential?.expiresAt ?: dto.expiresAt,
            pages = pages,
            // Assignments only mean anything against a page we know about.
            assignments = assignments.filterValues { pageId -> pages.any { it.id == pageId } },
        )
    }

    private fun pageFromDto(dto: OrgLinkedInPageDto): OrgLinkedInPage? {
        val id = dto.resolvedId ?: return null
        return OrgLinkedInPage(
            id = id,
            linkedInPageId = dto.linkedInPageId,
            name = dto.resolvedName,
            logoUrl = dto.resolvedLogo,
            lastSyncedAt = dto.lastSyncedAt,
        )
    }
}
