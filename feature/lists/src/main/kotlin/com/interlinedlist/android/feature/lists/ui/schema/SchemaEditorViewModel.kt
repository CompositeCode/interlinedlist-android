package com.interlinedlist.android.feature.lists.ui.schema

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.lists.data.ListsRepository
import com.interlinedlist.android.feature.lists.domain.FieldType
import com.interlinedlist.android.feature.lists.domain.ListSchema
import com.interlinedlist.android.feature.lists.domain.ListSummary
import com.interlinedlist.android.feature.lists.domain.SchemaField
import com.interlinedlist.android.feature.lists.ui.isSubscriptionGate
import com.interlinedlist.android.feature.lists.ui.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The nav argument key the schema editor route reads its list id from. */
const val SCHEMA_LIST_ID_ARG = "listId"

/**
 * One editable column in the schema editor. A stable [uiId] keys the row in the
 * list so reordering/removal stays correct while the user types; it is never sent
 * to the server (the [key]/[label]/[type] become the DSL).
 */
data class EditableColumn(
    val uiId: Long,
    val key: String = "",
    val label: String = "",
    val type: FieldType = FieldType.TEXT,
    /** The server assigns this column's value (GitHub's `number`, `url`, …). */
    val readOnly: Boolean = false,
) {
    /** True once the column has a usable key to persist. */
    val isComplete: Boolean get() = key.isNotBlank()
}

/** UI state for the schema editor. */
data class SchemaEditorUiState(
    val columns: List<EditableColumn> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val subscriptionRequired: Boolean = false,
    val saved: Boolean = false,
    /**
     * True for a GitHub-backed list, whose columns are the fixed GitHub issue
     * fields. The editor is then **locked to the parent list**: the columns are
     * shown read-only and only [parentId] can change. Letting someone edit a
     * fixed schema would produce a confusing server error at save time instead of
     * an honest "you cannot change this" up front.
     */
    val isSchemaLocked: Boolean = false,
    /** `"owner/repo"` behind a locked schema, for the explanation copy. */
    val githubRepo: String? = null,
    /** The list this one currently hangs under, if any. */
    val parentId: String? = null,
    /** Other lists that could be this list's parent. */
    val parentOptions: List<ListSummary> = emptyList(),
) {
    /**
     * Save is allowed once at least one column has a key and nothing is in
     * flight — and never on a locked schema.
     */
    val canSave: Boolean get() = !isSchemaLocked && !isSaving && columns.any { it.isComplete }

    /** Columns may be added/removed/retyped only on an unlocked schema. */
    val canEditColumns: Boolean get() = !isSchemaLocked
}

@HiltViewModel
class SchemaEditorViewModel @Inject constructor(
    private val repository: ListsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val listId: String = requireNotNull(savedStateHandle[SCHEMA_LIST_ID_ARG]) {
        "SchemaEditorViewModel requires a '$SCHEMA_LIST_ID_ARG' nav argument"
    }

    private var nextUiId = 0L

    private val _uiState = MutableStateFlow(SchemaEditorUiState())
    val uiState: StateFlow<SchemaEditorUiState> = _uiState.asStateFlow()

    init {
        load()
        observeParentOptions()
    }

    /**
     * Candidate parent lists, straight from the offline-first cache — the picker
     * is the only edit a locked (GitHub-backed) schema still allows, and it does
     * not warrant its own network call. A list cannot parent itself.
     */
    private fun observeParentOptions() {
        viewModelScope.launch {
            repository.observeLists().collect { lists ->
                _uiState.update { state ->
                    state.copy(parentOptions = lists.filterNot { it.id == listId })
                }
            }
        }
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null, subscriptionRequired = false) }
        viewModelScope.launch {
            when (val result = repository.getListDetail(listId)) {
                is ApiResult.Success -> _uiState.update {
                    val summary = result.data.summary
                    it.copy(
                        columns = result.data.schema.fields.map(::toEditable),
                        isLoading = false,
                        isSchemaLocked = summary.isGithubBacked,
                        githubRepo = summary.githubRepo,
                        parentId = summary.parentId,
                    )
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = result.error.toUserMessage(),
                        subscriptionRequired = result.error.isSubscriptionGate,
                    )
                }
            }
        }
    }

    fun addColumn() {
        if (_uiState.value.isSchemaLocked) return
        _uiState.update { it.copy(columns = it.columns + EditableColumn(uiId = nextUiId++)) }
    }

    fun removeColumn(uiId: Long) {
        if (_uiState.value.isSchemaLocked) return
        _uiState.update { it.copy(columns = it.columns.filterNot { column -> column.uiId == uiId }) }
    }

    fun updateKey(uiId: Long, key: String) = mutate(uiId) { it.copy(key = key) }

    fun updateLabel(uiId: Long, label: String) = mutate(uiId) { it.copy(label = label) }

    fun updateType(uiId: Long, type: FieldType) = mutate(uiId) { it.copy(type = type) }

    /**
     * Re-parents the list. This is the whole of what a locked schema allows, and
     * it works the same on an unlocked one.
     */
    fun setParent(parentId: String, onDone: () -> Unit = {}) {
        if (_uiState.value.isSaving || parentId == _uiState.value.parentId) return
        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.updateList(id = listId, parentId = parentId)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isSaving = false, parentId = result.data.parentId ?: parentId) }
                    onDone()
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

    fun save(onSaved: () -> Unit = {}) {
        // A GitHub-backed list's columns are GitHub's; the server would reject
        // the write, so it is refused here with the UI already saying why.
        if (_uiState.value.isSchemaLocked) return
        val schema = toSchema()
        if (schema.isEmpty) return
        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.updateSchema(listId, schema)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            saved = true,
                            columns = result.data.fields.map(::toEditable),
                        )
                    }
                    onSaved()
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

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }

    /** Projects the editable rows (dropping incomplete ones) into a [ListSchema]. */
    private fun toSchema(): ListSchema = ListSchema(
        _uiState.value.columns
            .filter { it.isComplete }
            .map { column ->
                SchemaField(
                    key = column.key.trim(),
                    label = column.label.trim().ifBlank { column.key.trim() },
                    type = column.type,
                )
            },
    )

    private fun toEditable(field: SchemaField): EditableColumn = EditableColumn(
        uiId = nextUiId++,
        key = field.key,
        label = field.label,
        type = field.type,
        readOnly = field.readOnly,
    )

    private fun mutate(uiId: Long, transform: (EditableColumn) -> EditableColumn) {
        if (_uiState.value.isSchemaLocked) return
        _uiState.update { state ->
            state.copy(columns = state.columns.map { if (it.uiId == uiId) transform(it) else it })
        }
    }
}
