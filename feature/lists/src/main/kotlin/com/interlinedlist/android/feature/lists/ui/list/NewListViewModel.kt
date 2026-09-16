package com.interlinedlist.android.feature.lists.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.lists.data.GithubRepository
import com.interlinedlist.android.feature.lists.data.ListsRepository
import com.interlinedlist.android.feature.lists.domain.GithubOrg
import com.interlinedlist.android.feature.lists.domain.GithubRepo
import com.interlinedlist.android.feature.lists.domain.ListSummary
import com.interlinedlist.android.feature.lists.ui.github.GithubLinkProblem
import com.interlinedlist.android.feature.lists.ui.github.toGithubLinkProblem
import com.interlinedlist.android.feature.lists.ui.isSubscriptionGate
import com.interlinedlist.android.feature.lists.ui.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which kind of list the "New list" sheet is creating. */
enum class NewListKind { LOCAL, GITHUB }

/**
 * State of the "New list" sheet, covering both kinds. The GitHub half only loads
 * once the user switches to it, so someone creating a local list never pays for a
 * GitHub round trip.
 */
data class NewListUiState(
    val kind: NewListKind = NewListKind.LOCAL,
    val title: String = "",
    val isPublic: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val subscriptionRequired: Boolean = false,
    // --- GitHub half -----------------------------------------------------
    val isLoadingRepos: Boolean = false,
    val orgs: List<GithubOrg> = emptyList(),
    /** `null` = "All repositories"; otherwise the org login passed as `?org=`. */
    val selectedOrg: String? = null,
    val repos: List<GithubRepo> = emptyList(),
    val repoQuery: String = "",
    val selectedRepo: GithubRepo? = null,
    /** Set when GitHub is unlinked or refuses the token; blocks the picker with an explanation. */
    val linkProblem: GithubLinkProblem? = null,
) {
    /** Repositories matching the filter box, owner and name both searched. */
    val visibleRepos: List<GithubRepo>
        get() = repoQuery.trim().takeIf { it.isNotEmpty() }?.let { q ->
            repos.filter { it.fullName.contains(q, ignoreCase = true) }
        } ?: repos

    /**
     * True when the linked account has no reachable repositories. Usually means
     * the OAuth app has not been approved for the user's organisations.
     */
    val hasNoRepos: Boolean
        get() = kind == NewListKind.GITHUB && !isLoadingRepos && linkProblem == null && repos.isEmpty()

    val canCreate: Boolean
        get() = !isSaving && when (kind) {
            NewListKind.LOCAL -> title.isNotBlank()
            NewListKind.GITHUB -> selectedRepo != null
        }
}

/**
 * Backs the "New list" sheet: a local list (title + visibility) or a
 * GitHub-backed one (pick a repository whose issues become the rows).
 *
 * It owns the repo picker rather than [ListsViewModel] so the index keeps its
 * single job, and so nothing GitHub-related is fetched until the sheet asks.
 */
@HiltViewModel
class NewListViewModel @Inject constructor(
    private val listsRepository: ListsRepository,
    private val githubRepository: GithubRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewListUiState())
    val uiState: StateFlow<NewListUiState> = _uiState.asStateFlow()

    fun selectKind(kind: NewListKind) {
        if (_uiState.value.kind == kind) return
        _uiState.update { it.copy(kind = kind, errorMessage = null) }
        // Repos are fetched lazily, and only once.
        if (kind == NewListKind.GITHUB && _uiState.value.repos.isEmpty()) loadRepos()
    }

    fun onTitleChange(title: String) = _uiState.update { it.copy(title = title) }

    fun onPublicChange(isPublic: Boolean) = _uiState.update { it.copy(isPublic = isPublic) }

    fun onRepoQueryChange(query: String) = _uiState.update { it.copy(repoQuery = query) }

    /**
     * Picks a repository. The title defaults to the repository name (as the web
     * app does) unless the user has already typed one.
     */
    fun selectRepo(repo: GithubRepo) = _uiState.update { state ->
        state.copy(
            selectedRepo = repo,
            title = state.title.ifBlank { repo.name },
        )
    }

    /** Scopes the picker to one organisation (`?org=`), or to everything when null. */
    fun selectOrg(login: String?) {
        if (_uiState.value.selectedOrg == login) return
        _uiState.update { it.copy(selectedOrg = login, selectedRepo = null) }
        loadRepos()
    }

    /**
     * Loads the organisations and repositories the linked account can reach.
     *
     * The org list is decoration for the filter: if it fails, the picker still
     * works unscoped, so only the repo call can put the sheet into an error state.
     */
    fun loadRepos() {
        _uiState.update { it.copy(isLoadingRepos = true, errorMessage = null, linkProblem = null) }
        viewModelScope.launch {
            if (_uiState.value.orgs.isEmpty()) {
                when (val orgs = githubRepository.getOrgs()) {
                    is ApiResult.Success -> _uiState.update { it.copy(orgs = orgs.data) }
                    is ApiResult.Failure -> Unit // Filter is optional; keep going.
                }
            }
            when (val result = githubRepository.getRepos(_uiState.value.selectedOrg)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isLoadingRepos = false, repos = result.data)
                }
                is ApiResult.Failure -> {
                    val problem = result.error.toGithubLinkProblem()
                    _uiState.update {
                        it.copy(
                            isLoadingRepos = false,
                            repos = emptyList(),
                            linkProblem = problem,
                            // A link problem is explained by its own panel; only
                            // ordinary failures need the generic message.
                            errorMessage = if (problem == null) result.error.toUserMessage() else null,
                        )
                    }
                }
            }
        }
    }

    /** Creates the list described by the current state and hands it to [onCreated]. */
    fun create(onCreated: (ListSummary) -> Unit) {
        val state = _uiState.value
        if (!state.canCreate) return
        _uiState.update { it.copy(isSaving = true, errorMessage = null, subscriptionRequired = false) }
        viewModelScope.launch {
            val result = when (state.kind) {
                NewListKind.LOCAL -> listsRepository.createList(
                    title = state.title.trim(),
                    isPublic = state.isPublic,
                )
                NewListKind.GITHUB -> listsRepository.createGithubList(
                    repo = state.selectedRepo!!.fullName,
                    title = state.title.trim().ifBlank { state.selectedRepo.name },
                    isPublic = state.isPublic,
                )
            }
            when (result) {
                is ApiResult.Success -> {
                    _uiState.value = NewListUiState() // Ready for the next one.
                    onCreated(result.data)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = result.error.toUserMessage(),
                        subscriptionRequired = result.error.isSubscriptionGate,
                    )
                }
            }
        }
    }

    fun reset() {
        _uiState.value = NewListUiState()
    }
}
