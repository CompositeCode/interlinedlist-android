package com.interlinedlist.android.feature.integrations.ui.github

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.integrations.data.IntegrationsRepository
import com.interlinedlist.android.feature.integrations.data.isGitHubNotLinked
import com.interlinedlist.android.feature.integrations.domain.GitHubAssignee
import com.interlinedlist.android.feature.integrations.domain.GitHubIssue
import com.interlinedlist.android.feature.integrations.domain.GitHubLabel
import com.interlinedlist.android.feature.integrations.domain.GitHubRepo
import com.interlinedlist.android.feature.integrations.ui.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the GitHub section. Modelled as one flat state so the screen can
 * render three panes off it: the repo list, the selected repo's issues, and the
 * create-issue composer.
 *
 * [notConnected] is the graceful "connect GitHub" state, set when the API reports
 * the account isn't linked; the screen shows a connect prompt instead of an error.
 */
data class GitHubUiState(
    val isLoadingRepos: Boolean = true,
    val notConnected: Boolean = false,
    val repos: List<GitHubRepo> = emptyList(),
    val reposError: String? = null,

    val selectedRepo: GitHubRepo? = null,
    val isLoadingIssues: Boolean = false,
    val issues: List<GitHubIssue> = emptyList(),
    val issuesError: String? = null,

    // Composer context for the selected repo.
    val labels: List<GitHubLabel> = emptyList(),
    val assignees: List<GitHubAssignee> = emptyList(),

    val isCreatingIssue: Boolean = false,
    val createError: String? = null,

    val commentingOn: Int? = null,
    val commentError: String? = null,
    /** One-shot user-facing confirmations (issue created, comment added). */
    val message: String? = null,
)

@HiltViewModel
class GitHubViewModel @Inject constructor(
    private val repository: IntegrationsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GitHubUiState())
    val uiState: StateFlow<GitHubUiState> = _uiState.asStateFlow()

    init { loadRepos() }

    fun loadRepos() {
        _uiState.update { it.copy(isLoadingRepos = true, reposError = null, notConnected = false) }
        viewModelScope.launch {
            when (val result = repository.getGitHubRepos()) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isLoadingRepos = false, repos = result.data)
                }
                is ApiResult.Failure -> _uiState.update {
                    if (result.error.isGitHubNotLinked()) {
                        it.copy(isLoadingRepos = false, notConnected = true, repos = emptyList())
                    } else {
                        it.copy(isLoadingRepos = false, reposError = result.error.toUserMessage())
                    }
                }
            }
        }
    }

    /** Opens a repo and loads its issues plus its labels/assignees for the composer. */
    fun selectRepo(repo: GitHubRepo) {
        _uiState.update {
            it.copy(
                selectedRepo = repo,
                isLoadingIssues = true,
                issues = emptyList(),
                issuesError = null,
                labels = emptyList(),
                assignees = emptyList(),
                createError = null,
            )
        }
        viewModelScope.launch {
            when (val result = repository.getGitHubIssues(repo.fullName)) {
                is ApiResult.Success -> _uiState.update { it.copy(isLoadingIssues = false, issues = result.data) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isLoadingIssues = false, issuesError = result.error.toUserMessage())
                }
            }
        }
        // Composer context is best-effort; failures leave the pickers empty.
        viewModelScope.launch {
            val labels = (repository.getGitHubLabels(repo.owner, repo.name) as? ApiResult.Success)?.data.orEmpty()
            val assignees = (repository.getGitHubAssignees(repo.owner, repo.name) as? ApiResult.Success)?.data.orEmpty()
            _uiState.update { it.copy(labels = labels, assignees = assignees) }
        }
    }

    /** Returns to the repo list. */
    fun clearSelectedRepo() {
        _uiState.update {
            it.copy(
                selectedRepo = null,
                issues = emptyList(),
                issuesError = null,
                labels = emptyList(),
                assignees = emptyList(),
            )
        }
    }

    fun refreshIssues() {
        val repo = _uiState.value.selectedRepo ?: return
        _uiState.update { it.copy(isLoadingIssues = true, issuesError = null) }
        viewModelScope.launch {
            when (val result = repository.getGitHubIssues(repo.fullName)) {
                is ApiResult.Success -> _uiState.update { it.copy(isLoadingIssues = false, issues = result.data) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isLoadingIssues = false, issuesError = result.error.toUserMessage())
                }
            }
        }
    }

    /**
     * Creates an issue on the selected repo. On success the new issue is prepended
     * to the list optimistically (in addition to being persisted server-side), so
     * the user sees it immediately without waiting for a refresh.
     */
    fun createIssue(
        title: String,
        body: String?,
        labels: List<String> = emptyList(),
        assignees: List<String> = emptyList(),
    ) {
        val repo = _uiState.value.selectedRepo ?: return
        if (title.isBlank() || _uiState.value.isCreatingIssue) return
        _uiState.update { it.copy(isCreatingIssue = true, createError = null) }
        viewModelScope.launch {
            when (val result = repository.createGitHubIssue(repo.fullName, title.trim(), body, labels, assignees)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        isCreatingIssue = false,
                        issues = listOf(result.data) + it.issues,
                        message = "Issue created",
                    )
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isCreatingIssue = false, createError = result.error.toUserMessage())
                }
            }
        }
    }

    /** Adds a comment to [issue] on the selected repo. */
    fun addComment(issue: GitHubIssue, body: String) {
        val repo = _uiState.value.selectedRepo ?: return
        if (body.isBlank() || _uiState.value.commentingOn != null) return
        _uiState.update { it.copy(commentingOn = issue.number, commentError = null) }
        viewModelScope.launch {
            when (val result = repository.addGitHubIssueComment(repo.owner, repo.name, issue.number, body.trim())) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(commentingOn = null, message = "Comment added")
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(commentingOn = null, commentError = result.error.toUserMessage())
                }
            }
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }
    fun clearCreateError() = _uiState.update { it.copy(createError = null) }
    fun clearCommentError() = _uiState.update { it.copy(commentError = null) }
}
