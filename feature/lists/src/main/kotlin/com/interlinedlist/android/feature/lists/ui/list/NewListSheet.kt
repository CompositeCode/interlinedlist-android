package com.interlinedlist.android.feature.lists.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.interlinedlist.android.feature.lists.domain.GithubRepo
import com.interlinedlist.android.feature.lists.domain.GithubRepoLink
import com.interlinedlist.android.feature.lists.ui.github.GithubLinkProblem

/** Stable test tags for the "New list" sheet. */
object NewListTestTags {
    const val SHEET = "newListSheet"
    const val KIND_LOCAL = "newListKindLocal"
    const val KIND_GITHUB = "newListKindGithub"
    const val TITLE = "newListTitle"
    const val VISIBILITY = "newListVisibility"
    const val CREATE = "newListCreate"
    const val CANCEL = "newListCancel"
    const val ERROR = "newListError"
    const val REPO_SEARCH = "newListRepoSearch"
    const val REPO_PROGRESS = "newListRepoProgress"
    const val REPO_EMPTY = "newListRepoEmpty"
    const val LINK_PROBLEM = "newListLinkProblem"
    const val LINK_ACTION = "newListLinkAction"
    fun repo(fullName: String) = "newListRepo_$fullName"
    fun org(login: String) = "newListOrg_$login"
}

/**
 * Create form for a new list: a local one, or one backed by a GitHub repository's
 * issues.
 *
 * The GitHub half needs a linked GitHub account with the Issues scope. Linking
 * happens in the browser OAuth flow, which this app does not drive, so when the
 * account is unlinked or refused the picker is replaced by an explanation and a
 * route to the connected-accounts screen — never an empty, silently broken list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewListSheet(
    state: NewListUiState,
    onSelectKind: (NewListKind) -> Unit,
    onTitleChange: (String) -> Unit,
    onPublicChange: (Boolean) -> Unit,
    onRepoQueryChange: (String) -> Unit,
    onSelectRepo: (GithubRepo) -> Unit,
    onSelectOrg: (String?) -> Unit,
    onCreate: () -> Unit,
    onCancel: () -> Unit,
    onOpenConnectedAccounts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag(NewListTestTags.SHEET),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("New list", style = MaterialTheme.typography.titleLarge)

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = state.kind == NewListKind.LOCAL,
                onClick = { onSelectKind(NewListKind.LOCAL) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                modifier = Modifier.testTag(NewListTestTags.KIND_LOCAL),
            ) { Text("Local list") }
            SegmentedButton(
                selected = state.kind == NewListKind.GITHUB,
                onClick = { onSelectKind(NewListKind.GITHUB) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                modifier = Modifier.testTag(NewListTestTags.KIND_GITHUB),
            ) { Text("GitHub-backed") }
        }

        if (state.kind == NewListKind.GITHUB) {
            RepoPicker(
                state = state,
                onRepoQueryChange = onRepoQueryChange,
                onSelectRepo = onSelectRepo,
                onSelectOrg = onSelectOrg,
                onOpenConnectedAccounts = onOpenConnectedAccounts,
            )
        }

        // With no repository chosen there is nothing to title yet.
        if (state.kind == NewListKind.LOCAL || state.selectedRepo != null) {
            OutlinedTextField(
                value = state.title,
                onValueChange = onTitleChange,
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(NewListTestTags.TITLE),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Public list", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = if (state.kind == NewListKind.GITHUB) {
                            // Two different visibilities; do not let them blur.
                            "Who can see this list on InterlinedList. The repository's own " +
                                "visibility is set on GitHub and is not changed here."
                        } else {
                            "Anyone with the link can view"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.isPublic,
                    onCheckedChange = onPublicChange,
                    modifier = Modifier.testTag(NewListTestTags.VISIBILITY),
                )
            }
        }

        if (state.errorMessage != null) {
            Text(
                text = state.errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(NewListTestTags.ERROR),
            )
        }

        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.testTag(NewListTestTags.CANCEL),
            ) { Text("Cancel") }
            Button(
                onClick = onCreate,
                enabled = state.canCreate,
                modifier = Modifier.testTag(NewListTestTags.CREATE),
            ) { Text("Create") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepoPicker(
    state: NewListUiState,
    onRepoQueryChange: (String) -> Unit,
    onSelectRepo: (GithubRepo) -> Unit,
    onSelectOrg: (String?) -> Unit,
    onOpenConnectedAccounts: () -> Unit,
) {
    val problem = state.linkProblem
    if (problem != null) {
        LinkProblemPanel(problem, onOpenConnectedAccounts)
        return
    }

    Text(
        "Rows mirror the repository's issues: adding a row opens an issue, editing " +
            "updates it, deleting closes it.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (state.orgs.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = state.selectedOrg == null,
                onClick = { onSelectOrg(null) },
                label = { Text("All") },
                modifier = Modifier.testTag(NewListTestTags.org("all")),
            )
            state.orgs.forEach { org ->
                FilterChip(
                    selected = state.selectedOrg == org.login,
                    onClick = { onSelectOrg(org.login) },
                    label = { Text(org.login) },
                    modifier = Modifier.testTag(NewListTestTags.org(org.login)),
                )
            }
        }
    }

    OutlinedTextField(
        value = state.repoQuery,
        onValueChange = onRepoQueryChange,
        label = { Text("Find a repository") },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(NewListTestTags.REPO_SEARCH),
    )

    when {
        state.isLoadingRepos -> Box(
            Modifier.fillMaxWidth().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator(Modifier.size(24.dp).testTag(NewListTestTags.REPO_PROGRESS)) }

        state.hasNoRepos -> Text(
            text = "No repositories came back for this account. If the repository belongs " +
                "to an organisation, that organisation has to approve InterlinedList on " +
                "GitHub before its repositories appear here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(NewListTestTags.REPO_EMPTY),
        )

        else -> LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 260.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.visibleRepos, key = { it.fullName }) { repo ->
                RepoRow(
                    repo = repo,
                    selected = state.selectedRepo?.fullName == repo.fullName,
                    onClick = { onSelectRepo(repo) },
                )
            }
        }
    }
}

@Composable
private fun RepoRow(repo: GithubRepo, selected: Boolean, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(NewListTestTags.repo(repo.fullName)),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = repo.fullName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (repo.isPrivate) {
                    // The repository is private on GitHub — not the list.
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = GithubRepoLink.PRIVATE_TAG,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = GithubRepoLink.PRIVATE_TAG,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!repo.description.isNullOrBlank()) {
                Text(
                    text = repo.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Replaces the picker when GitHub is unlinked or refuses the linked account. */
@Composable
private fun LinkProblemPanel(problem: GithubLinkProblem, onOpenConnectedAccounts: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(NewListTestTags.LINK_PROBLEM),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(problem.title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = problem.explanation,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AssistChip(
            onClick = onOpenConnectedAccounts,
            label = { Text(problem.actionLabel) },
            modifier = Modifier.testTag(NewListTestTags.LINK_ACTION),
        )
    }
}
