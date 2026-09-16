package com.interlinedlist.android.feature.lists.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.interlinedlist.android.feature.lists.domain.GithubRepoLink

/** Stable test tags for the repository link under a GitHub-backed list's title. */
object GithubRepoLinkTestTags {
    const val ROW = "listGithubRepoLink"
    const val LINK = "listGithubRepoLinkText"
    const val PRIVATE_TAG = "listGithubRepoPrivateTag"
    const val PRIVATE_EXPLANATION = "listGithubRepoPrivateExplanation"
}

/**
 * The repository a GitHub-backed list came from, linked under the list title —
 * `owner/repo issues`, opening that repository's issues page on GitHub.
 *
 * When [githubRepoPrivate] is true the link carries a **Private repo** tag.
 * That tag is about the *repository's* visibility on GitHub, never the list's:
 * the two are set separately, so someone invited to this list may have no access
 * to the repository and will be met by a GitHub sign-in or "not found" page. The
 * explanation is one tap away rather than hidden, because getting this backwards
 * is how people end up wrong about who can see their data.
 *
 * A null [githubRepoPrivate] means the visibility has not been recorded yet (a
 * list that has not synced since the tag existed), so no tag is shown — an
 * unknown visibility is never presented as public.
 */
@Composable
fun GithubRepoLinkRow(
    repo: String,
    githubRepoPrivate: Boolean?,
    onOpenRepo: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (repo.isBlank()) return
    var explanationShown by remember(repo) { mutableStateOf(false) }
    val isPrivate = GithubRepoLink.showsPrivateTag(githubRepoPrivate)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag(GithubRepoLinkTestTags.ROW),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = GithubRepoLink.label(repo),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clickable { onOpenRepo(GithubRepoLink.issuesUrl(repo)) }
                    .testTag(GithubRepoLinkTestTags.LINK),
            )
            if (isPrivate) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clickable { explanationShown = !explanationShown }
                        .testTag(GithubRepoLinkTestTags.PRIVATE_TAG),
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = GithubRepoLink.PRIVATE_TAG,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (isPrivate && explanationShown) {
            Text(
                text = GithubRepoLink.PRIVATE_TAG_EXPLANATION,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .testTag(GithubRepoLinkTestTags.PRIVATE_EXPLANATION),
            )
        }
    }
}
