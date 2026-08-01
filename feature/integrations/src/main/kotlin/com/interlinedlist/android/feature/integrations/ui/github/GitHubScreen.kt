package com.interlinedlist.android.feature.integrations.ui.github

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.integrations.domain.GitHubIssue
import com.interlinedlist.android.feature.integrations.domain.GitHubRepo

/** Stable test tags so UI/instrumented tests can address the GitHub controls. */
object GitHubTestTags {
    const val REPO_LIST = "githubRepoList"
    const val REPOS_PROGRESS = "githubReposProgress"
    const val NOT_CONNECTED = "githubNotConnected"
    const val EMPTY_REPOS = "githubEmptyRepos"
    const val ISSUE_LIST = "githubIssueList"
    const val ISSUES_PROGRESS = "githubIssuesProgress"
    const val EMPTY_ISSUES = "githubEmptyIssues"
    const val NEW_ISSUE_FAB = "githubNewIssueFab"
    const val COMPOSER = "githubComposer"
    const val COMPOSER_TITLE = "githubComposerTitle"
    const val COMPOSER_BODY = "githubComposerBody"
    const val COMPOSER_SUBMIT = "githubComposerSubmit"
    fun repo(fullName: String) = "githubRepo_$fullName"
    fun issue(number: Int) = "githubIssue_$number"
}

/**
 * Hilt-wired entry point for the GitHub section. Reached from the Integrations
 * hub; lists connected repos, drills into a repo's issues, and lets the user
 * create an issue or comment on one.
 */
@Composable
fun GitHubRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GitHubViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    GitHubScreen(
        state = state,
        onBack = onBack,
        onRetryRepos = viewModel::loadRepos,
        onSelectRepo = viewModel::selectRepo,
        onClearRepo = viewModel::clearSelectedRepo,
        onCreateIssue = { title, body, labels, assignees ->
            viewModel.createIssue(title, body, labels, assignees)
        },
        onAddComment = viewModel::addComment,
        onMessageShown = viewModel::clearMessage,
        onCreateErrorShown = viewModel::clearCreateError,
        onCommentErrorShown = viewModel::clearCommentError,
        modifier = modifier,
    )
}

/** Stateless GitHub UI — easy to preview and to drive from Compose tests. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHubScreen(
    state: GitHubUiState,
    onBack: () -> Unit,
    onRetryRepos: () -> Unit,
    onSelectRepo: (GitHubRepo) -> Unit,
    onClearRepo: () -> Unit,
    onCreateIssue: (title: String, body: String?, labels: List<String>, assignees: List<String>) -> Unit,
    onAddComment: (GitHubIssue, String) -> Unit,
    onMessageShown: () -> Unit,
    onCreateErrorShown: () -> Unit,
    onCommentErrorShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbarHostState.showSnackbar(it); onMessageShown() }
    }
    LaunchedEffect(state.createError) {
        state.createError?.let { snackbarHostState.showSnackbar(it); onCreateErrorShown() }
    }
    LaunchedEffect(state.commentError) {
        state.commentError?.let { snackbarHostState.showSnackbar(it); onCommentErrorShown() }
    }

    val selected = state.selectedRepo
    var showComposer by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(selected?.fullName ?: "GitHub") },
                navigationIcon = {
                    IconButton(onClick = { if (selected != null) onClearRepo() else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (selected != null && !state.isLoadingIssues) {
                FloatingActionButton(
                    onClick = { showComposer = true },
                    modifier = Modifier.testTag(GitHubTestTags.NEW_ISSUE_FAB),
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New issue")
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                selected == null -> ReposPane(state, onRetryRepos, onSelectRepo)
                else -> IssuesPane(state, onAddComment)
            }
        }

        if (showComposer && selected != null) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showComposer = false },
                sheetState = sheetState,
                modifier = Modifier.testTag(GitHubTestTags.COMPOSER),
            ) {
                IssueComposer(
                    state = state,
                    onSubmit = { title, body, labels, assignees ->
                        onCreateIssue(title, body, labels, assignees)
                        showComposer = false
                    },
                    onCancel = { showComposer = false },
                )
            }
        }
    }
}

@Composable
private fun ReposPane(
    state: GitHubUiState,
    onRetry: () -> Unit,
    onSelect: (GitHubRepo) -> Unit,
) {
    when {
        state.isLoadingRepos -> Centered {
            CircularProgressIndicator(Modifier.testTag(GitHubTestTags.REPOS_PROGRESS))
        }
        state.notConnected -> Centered {
            EmptyState(
                testTag = GitHubTestTags.NOT_CONNECTED,
                title = "Connect GitHub",
                message = "Link your GitHub account on the InterlinedList website to browse " +
                    "repositories and manage issues here.",
            )
        }
        state.reposError != null -> Centered {
            EmptyState(
                testTag = "githubReposError",
                title = "Couldn't load repositories",
                message = state.reposError,
                action = "Retry" to onRetry,
            )
        }
        state.repos.isEmpty() -> Centered {
            EmptyState(
                testTag = GitHubTestTags.EMPTY_REPOS,
                title = "No repositories",
                message = "GitHub is connected, but no repositories are available yet.",
            )
        }
        else -> LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .testTag(GitHubTestTags.REPO_LIST),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            items(state.repos, key = { it.fullName }) { repo -> RepoRow(repo, onSelect) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepoRow(repo: GitHubRepo, onSelect: (GitHubRepo) -> Unit) {
    Card(
        onClick = { onSelect(repo) },
        modifier = Modifier.fillMaxWidth().testTag(GitHubTestTags.repo(repo.fullName)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(repo.fullName, style = MaterialTheme.typography.titleMedium)
                    if (repo.isPrivate) {
                        Spacer(Modifier.size(6.dp))
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Private",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                repo.description?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun IssuesPane(
    state: GitHubUiState,
    onAddComment: (GitHubIssue, String) -> Unit,
) {
    when {
        state.isLoadingIssues -> Centered {
            CircularProgressIndicator(Modifier.testTag(GitHubTestTags.ISSUES_PROGRESS))
        }
        state.issuesError != null -> Centered {
            EmptyState(
                testTag = "githubIssuesError",
                title = "Couldn't load issues",
                message = state.issuesError,
            )
        }
        state.issues.isEmpty() -> Centered {
            EmptyState(
                testTag = GitHubTestTags.EMPTY_ISSUES,
                title = "No issues",
                message = "This repository has no issues yet. Tap + to create one.",
            )
        }
        else -> LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .testTag(GitHubTestTags.ISSUE_LIST),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            items(state.issues, key = { it.number }) { issue ->
                IssueRow(
                    issue = issue,
                    isCommenting = state.commentingOn == issue.number,
                    onAddComment = { body -> onAddComment(issue, body) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IssueRow(
    issue: GitHubIssue,
    isCommenting: Boolean,
    onAddComment: (String) -> Unit,
) {
    var comment by remember { mutableStateOf("") }
    var showComment by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth().testTag(GitHubTestTags.issue(issue.number))) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = "#${issue.number} · ${issue.title}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = if (issue.isOpen) "Open" else "Closed",
                style = MaterialTheme.typography.labelSmall,
                color = if (issue.isOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (issue.labels.isNotEmpty()) {
                Spacer(Modifier.size(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    issue.labels.forEach { label ->
                        AssistChip(onClick = {}, label = { Text(label) })
                    }
                }
            }
            issue.body?.let {
                Spacer(Modifier.size(6.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.size(8.dp))
            if (showComment) {
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Comment") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isCommenting,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showComment = false }) { Text("Cancel") }
                    OutlinedButton(
                        onClick = { onAddComment(comment); comment = "" },
                        enabled = comment.isNotBlank() && !isCommenting,
                    ) {
                        if (isCommenting) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Comment")
                        }
                    }
                }
            } else {
                TextButton(onClick = { showComment = true }) { Text("Add comment") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IssueComposer(
    state: GitHubUiState,
    onSubmit: (title: String, body: String?, labels: List<String>, assignees: List<String>) -> Unit,
    onCancel: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    val selectedLabels = remember { mutableStateOf(emptySet<String>()) }
    val selectedAssignees = remember { mutableStateOf(emptySet<String>()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("New issue", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(GitHubTestTags.COMPOSER_TITLE),
        )
        OutlinedTextField(
            value = body,
            onValueChange = { body = it },
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth().testTag(GitHubTestTags.COMPOSER_BODY),
        )
        if (state.labels.isNotEmpty()) {
            Text("Labels", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                state.labels.forEach { label ->
                    val checked = label.name in selectedLabels.value
                    FilterChip(
                        selected = checked,
                        onClick = {
                            selectedLabels.value = selectedLabels.value.toggle(label.name)
                        },
                        label = { Text(label.name) },
                    )
                }
            }
        }
        if (state.assignees.isNotEmpty()) {
            Text("Assignees", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                state.assignees.forEach { assignee ->
                    val checked = assignee.login in selectedAssignees.value
                    FilterChip(
                        selected = checked,
                        onClick = {
                            selectedAssignees.value = selectedAssignees.value.toggle(assignee.login)
                        },
                        label = { Text(assignee.login) },
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            OutlinedButton(
                onClick = {
                    onSubmit(
                        title,
                        body.ifBlank { null },
                        selectedLabels.value.toList(),
                        selectedAssignees.value.toList(),
                    )
                },
                enabled = title.isNotBlank() && !state.isCreatingIssue,
                modifier = Modifier.testTag(GitHubTestTags.COMPOSER_SUBMIT),
            ) {
                Text("Create issue")
            }
        }
    }
}

private fun Set<String>.toggle(value: String): Set<String> =
    if (value in this) this - value else this + value

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun EmptyState(
    testTag: String,
    title: String,
    message: String,
    action: Pair<String, () -> Unit>? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        action?.let { (label, onClick) ->
            OutlinedButton(onClick = onClick) { Text(label) }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun GitHubReposPreview() {
    InterlinedListTheme {
        GitHubScreen(
            state = GitHubUiState(
                isLoadingRepos = false,
                repos = listOf(
                    GitHubRepo("adron", "interlinedlist-android", description = "The app"),
                    GitHubRepo("adron", "notes", isPrivate = true),
                ),
            ),
            onBack = {},
            onRetryRepos = {},
            onSelectRepo = {},
            onClearRepo = {},
            onCreateIssue = { _, _, _, _ -> },
            onAddComment = { _, _ -> },
            onMessageShown = {},
            onCreateErrorShown = {},
            onCommentErrorShown = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun GitHubNotConnectedPreview() {
    InterlinedListTheme {
        GitHubScreen(
            state = GitHubUiState(isLoadingRepos = false, notConnected = true),
            onBack = {},
            onRetryRepos = {},
            onSelectRepo = {},
            onClearRepo = {},
            onCreateIssue = { _, _, _, _ -> },
            onAddComment = { _, _ -> },
            onMessageShown = {},
            onCreateErrorShown = {},
            onCommentErrorShown = {},
        )
    }
}
