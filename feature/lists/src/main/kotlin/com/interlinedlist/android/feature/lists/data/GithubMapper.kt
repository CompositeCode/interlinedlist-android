package com.interlinedlist.android.feature.lists.data

import com.interlinedlist.android.feature.lists.data.remote.dto.GithubOrgDto
import com.interlinedlist.android.feature.lists.data.remote.dto.GithubRepoDto
import com.interlinedlist.android.feature.lists.domain.GithubOrg
import com.interlinedlist.android.feature.lists.domain.GithubRepo

/**
 * Maps the lenient GitHub proxy DTOs to domain models. A repository that cannot
 * yield an owner **and** a name is dropped rather than shown half-populated: the
 * picker's whole output is an `owner/repo` string, and one that is missing a half
 * would be rejected by `POST /api/lists`.
 */
object GithubMapper {

    fun repoOrNull(dto: GithubRepoDto): GithubRepo? {
        val owner = dto.owner?.login?.nonBlank()
            ?: dto.ownerLogin?.nonBlank()
            ?: dto.fullName?.substringBefore('/', missingDelimiterValue = "")?.nonBlank()
        val name = dto.name?.nonBlank()
            ?: dto.fullName?.substringAfter('/', missingDelimiterValue = "")?.nonBlank()
        if (owner == null || name == null) return null
        return GithubRepo(
            owner = owner,
            name = name,
            isPrivate = dto.private ?: false,
            description = dto.description?.nonBlank(),
        )
    }

    /** An org without a login cannot be used as `?org=`, so it is dropped. */
    fun orgOrNull(dto: GithubOrgDto): GithubOrg? {
        val login = dto.login?.nonBlank() ?: dto.slug?.nonBlank() ?: dto.name?.nonBlank() ?: return null
        return GithubOrg(login = login, avatarUrl = dto.avatarUrl?.nonBlank())
    }

    private fun String.nonBlank(): String? = takeIf { it.isNotBlank() }
}
