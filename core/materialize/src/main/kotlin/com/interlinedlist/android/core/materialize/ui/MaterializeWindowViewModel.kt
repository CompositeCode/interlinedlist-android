package com.interlinedlist.android.core.materialize.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.materialize.data.MaterializeRepository
import com.interlinedlist.android.core.materialize.domain.DocumentListStyle
import com.interlinedlist.android.core.materialize.domain.ListColumnType
import com.interlinedlist.android.core.materialize.domain.MaterializeTarget
import com.interlinedlist.android.core.materialize.domain.RowDataStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the "Create from…" preview / edit / confirm window.
 *
 * The window edits a local draft and writes nothing until [confirm]; the
 * subscriber gate is not re-implemented here — `MaterializeRepository` refuses a
 * free account's creation before the request is built, and that refusal arrives
 * as `AppError.SubscriptionRequired`, which this exposes as
 * [MaterializeWindowUiState.subscriptionRequired] for the host's existing
 * subscription handling.
 *
 * The state is null until [start] supplies the source: the window is opened from
 * five different entry points with objects that are not nav arguments, so the
 * host hands them over once the composable is on screen.
 */
@HiltViewModel
class MaterializeWindowViewModel @Inject constructor(
    private val repository: MaterializeRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<MaterializeWindowUiState?>(null)
    val uiState: StateFlow<MaterializeWindowUiState?> = _uiState.asStateFlow()

    private var activeLaunch: MaterializeLaunch? = null
    private var nextUiId = 0L

    /** Opens the window. Re-opening with the same launch keeps the edits. */
    fun start(launch: MaterializeLaunch) {
        if (activeLaunch == launch) return
        activeLaunch = launch
        val state = MaterializeWindowUiState.from(launch)
        nextUiId = state.columns.size.toLong()
        _uiState.value = state
    }

    /**
     * Ends the flow: the next [start] opens a clean window even if it is handed
     * the identical launch.
     *
     * [start] deliberately keeps the edits when it is re-entered with the same
     * launch, so a recomposition or a rotation does not throw the user's work
     * away. That leaves the host to say when the flow is actually over — closing
     * the window — because only the host can tell the two apart.
     */
    fun reset() {
        activeLaunch = null
        nextUiId = 0L
        _uiState.value = null
    }

    /**
     * Switches destination from inside the window. Nothing is discarded here:
     * the edits stay in state and [MaterializeWindowUiState.toRequest] decides
     * which of them the new destination can carry.
     */
    fun selectTarget(target: MaterializeTarget) = mutate {
        if (it.target == target) it
        else it.copy(target = target, errorMessage = null, subscriptionRequired = false)
    }

    fun updateTitle(title: String) = mutate { it.copy(title = title, errorMessage = null) }

    fun updateDescription(description: String) = mutate { it.copy(description = description) }

    fun setPublic(isPublic: Boolean) = mutate { it.copy(isPublic = isPublic) }

    fun updateFileName(fileName: String) = mutate { it.copy(fileName = fileName) }

    fun selectListStyle(style: DocumentListStyle) = mutate { it.copy(listStyle = style) }

    fun selectRowDataStyle(style: RowDataStyle) = mutate { it.copy(rowDataStyle = style) }

    /** Adds an empty column — no source attribute, so it is created blank. */
    fun addColumn() = mutate {
        it.copy(columns = it.columns + EditableColumn(uiId = nextUiId++, name = ""))
    }

    fun removeColumn(uiId: Long) = mutate {
        it.copy(columns = it.columns.filterNot { column -> column.uiId == uiId })
    }

    fun renameColumn(uiId: Long, name: String) = mutateColumn(uiId) { it.copy(name = name) }

    fun changeColumnType(uiId: Long, type: ListColumnType) =
        mutateColumn(uiId) { it.copy(type = type) }

    fun dismissError() = mutate { it.copy(errorMessage = null, subscriptionRequired = false) }

    /** Creates what the window is previewing. The first write in the whole flow. */
    fun confirm() {
        val state = _uiState.value ?: return
        if (!state.canConfirm) {
            // Refused locally: the server would only answer the same thing.
            state.titleError?.let { error -> mutate { it.copy(errorMessage = error) } }
            return
        }

        val request = state.toRequest()
        mutate { it.copy(isSubmitting = true, errorMessage = null, subscriptionRequired = false) }
        viewModelScope.launch {
            when (val result = repository.materialize(request)) {
                is ApiResult.Success -> mutate {
                    it.copy(isSubmitting = false, success = result.data.toSuccess())
                }

                is ApiResult.Failure -> mutate {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = result.error.toUserMessage(),
                        subscriptionRequired = result.error.isSubscriptionGate,
                    )
                }
            }
        }
    }

    private fun mutate(transform: (MaterializeWindowUiState) -> MaterializeWindowUiState) =
        _uiState.update { state -> state?.let(transform) }

    private fun mutateColumn(uiId: Long, transform: (EditableColumn) -> EditableColumn) = mutate {
        it.copy(columns = it.columns.map { column -> if (column.uiId == uiId) transform(column) else column })
    }
}
