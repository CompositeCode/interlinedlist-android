package com.interlinedlist.android.feature.profile.ui.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.profile.data.ProfileRepository
import com.interlinedlist.android.feature.profile.domain.PublicListDetail
import com.interlinedlist.android.feature.profile.ui.common.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Nav arg keys the public-list route reads its owner username and list id from. */
const val PUBLIC_LIST_USERNAME_ARG = "username"
const val PUBLIC_LIST_ID_ARG = "listId"

/** UI state for the read-only public list view. */
data class PublicListUiState(
    val list: PublicListDetail? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

/**
 * Drives a read-only view of another user's public list (route
 * `publicList/{username}/{listId}`). Loads the list metadata and its rows from
 * `GET /api/users/{username}/lists/{id}` (+ `/data`). No caching (YAGNI).
 */
@HiltViewModel
class PublicListViewModel @Inject constructor(
    private val repository: ProfileRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val username: String = checkNotNull(savedStateHandle[PUBLIC_LIST_USERNAME_ARG]) {
        "PublicListViewModel requires a '$PUBLIC_LIST_USERNAME_ARG' nav arg"
    }
    private val listId: String = checkNotNull(savedStateHandle[PUBLIC_LIST_ID_ARG]) {
        "PublicListViewModel requires a '$PUBLIC_LIST_ID_ARG' nav arg"
    }

    private val _uiState = MutableStateFlow(PublicListUiState())
    val uiState: StateFlow<PublicListUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.getUserList(username, listId)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(list = result.data, isLoading = false)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }
}
