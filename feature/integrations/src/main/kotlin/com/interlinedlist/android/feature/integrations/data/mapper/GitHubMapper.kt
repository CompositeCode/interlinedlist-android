package com.interlinedlist.android.feature.integrations.data.mapper

import com.interlinedlist.android.feature.integrations.data.remote.dto.GitHubAssigneeDto
import com.interlinedlist.android.feature.integrations.data.remote.dto.GitHubIssueDto
import com.interlinedlist.android.feature.integrations.data.remote.dto.GitHubLabelDto
import com.interlinedlist.android.feature.integrations.data.remote.dto.GitHubRepoDto
import com.interlinedlist.android.feature.integrations.domain.GitHubAssignee
import com.interlinedlist.android.feature.integrations.domain.GitHubIssue
import com.interlinedlist.android.feature.integrations.domain.GitHubLabel
import com.interlinedlist.android.feature.integrations.domain.GitHubRepo

/**
 * Maps the lenient GitHub DTOs into domain models. Repos that can't yield an
 * owner/name pair (from a nested owner, a flattened `ownerLogin`, or `full_name`)
 * are dropped rather than surfaced half-populated. Issues without a number are
 * likewise dropped, since the number is the key the update/comment routes need.
 */

/** Drops repos we can't fully identify (no owner or no name). */
fun GitHubRepoDto.toDomainOrNull(): GitHubRepo? {
    val owner = owner?.login?.takeIf { it.isNotBlank() }
        ?: ownerLogin?.takeIf { it.isNotBlank() }
        ?: fullName?.substringBefore('/', missingDelimiterValue = "")?.takeIf { it.isNotBlank() }
    val repoName = name?.takeIf { it.isNotBlank() }
        ?: fullName?.substringAfter('/', missingDelimiterValue = "")?.takeIf { it.isNotBlank() }
    if (owner == null || repoName == null) return null
    return GitHubRepo(
        owner = owner,
        name = repoName,
        isPrivate = private ?: false,
        description = description?.takeIf { it.isNotBlank() },
    )
}

/** Drops issues without a number (needed by the update/comment routes). */
fun GitHubIssueDto.toDomainOrNull(): GitHubIssue? {
    val issueNumber = number ?: return null
    return GitHubIssue(
        number = issueNumber,
        title = title.orEmpty(),
        body = body?.takeIf { it.isNotBlank() },
        state = state?.takeIf { it.isNotBlank() } ?: "open",
        labels = labels.orEmpty().mapNotNull { it.name?.takeIf { n -> n.isNotBlank() } },
        assignees = assignees.orEmpty().mapNotNull { it.login?.takeIf { l -> l.isNotBlank() } },
    )
}

fun GitHubLabelDto.toDomainOrNull(): GitHubLabel? {
    val labelName = name?.takeIf { it.isNotBlank() } ?: return null
    return GitHubLabel(name = labelName, color = color?.takeIf { it.isNotBlank() })
}

fun GitHubAssigneeDto.toDomainOrNull(): GitHubAssignee? {
    val login = login?.takeIf { it.isNotBlank() } ?: return null
    return GitHubAssignee(login = login, avatarUrl = avatarUrl?.takeIf { it.isNotBlank() })
}
