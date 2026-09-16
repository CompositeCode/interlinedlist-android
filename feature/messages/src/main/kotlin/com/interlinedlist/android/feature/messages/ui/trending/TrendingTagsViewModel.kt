package com.interlinedlist.android.feature.messages.ui.trending

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.messages.data.MessagesRepository
import com.interlinedlist.android.feature.messages.domain.TrendingTag
import com.interlinedlist.android.feature.messages.domain.TrendingWindow
import com.interlinedlist.android.feature.messages.ui.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which of the four mutually exclusive things the trending surface is showing. */
enum class TrendingTagsStatus {
    /** The first load has not answered yet. */
    LOADING,

    /** Tags to offer. */
    TAGS,

    /**
     * The instance is genuinely quiet — nobody tagged a public message in the
     * window. A real answer, rendered as such rather than as nothing at all.
     */
    EMPTY,

    /** The lookup failed. Deliberately not the same thing as [EMPTY]. */
    ERROR,
}

/** State of the trending-tags surface. */
data class TrendingTagsUiState(
    /**
     * The trailing window the counts were requested for. The response never says
     * which period it covers, so this — the window the app *asked* for — is the
     * only honest source for the surface's wording.
     */
    val window: TrendingWindow = TrendingWindow.WEEK,
    val tags: List<TrendingTag> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    /**
     * The single place that decides what renders, so "empty" and "failed" can
     * never collapse into the same blank strip.
     *
     * Tags outrank both: a refresh that fails (or is still running) leaves the
     * doors that already work on screen rather than replacing them with a banner.
     */
    val status: TrendingTagsStatus
        get() = when {
            tags.isNotEmpty() -> TrendingTagsStatus.TAGS
            errorMessage != null -> TrendingTagsStatus.ERROR
            isLoading -> TrendingTagsStatus.LOADING
            else -> TrendingTagsStatus.EMPTY
        }

    /** e.g. "Trending this week" — derived from the window that was requested. */
    val title: String get() = "Trending ${window.label}"
}

/**
 * Loads `GET /api/tags/trending` for the feed's trending rail.
 *
 * Deliberately separate from `MessagesFeedViewModel`: the rail has its own
 * lifecycle (it survives a feed refresh, fails on its own, and retries on its
 * own), and folding four more fields into the feed's state would make a failed
 * trending lookup look like a failed feed.
 */
@HiltViewModel
class TrendingTagsViewModel @Inject constructor(
    private val repository: MessagesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrendingTagsUiState(isLoading = true))
    val uiState: StateFlow<TrendingTagsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /** (Re)loads the trending tags; also the Retry action of the error state. */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = repository.trendingTags(window = _uiState.value.window)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(tags = result.data, isLoading = false, errorMessage = null)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }
}
