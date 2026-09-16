package com.interlinedlist.android.feature.lists.domain

/**
 * Somebody else currently in a list's grid, as reported by the presence half of
 * `POST /api/lists/{id}/data/versions`. The server never includes the caller.
 *
 * Deliberately the same shape the documents editor's presence model uses — an
 * avatar cluster, not live cursors — plus the one fact a grid adds: which row the
 * person is on, so an edit can be shown as contended.
 */
data class ListPresence(
    val userId: String,
    val displayName: String? = null,
    val username: String? = null,
    /** The row this person currently has focused, when the server reports one. */
    val focusedRowId: String? = null,
    /** Server-assigned colour (e.g. `"#3366ff"`), kept verbatim. May be absent. */
    val color: String? = null,
) {
    val label: String
        get() = displayName?.takeIf { it.isNotBlank() }
            ?: username?.takeIf { it.isNotBlank() }
            ?: userId

    val initial: String get() = label.trim().firstOrNull()?.uppercase() ?: "?"
}

/**
 * One answer from the combined freshness poll / presence heartbeat.
 *
 * [changed] carries whole rows whose stored version differs from the one we sent,
 * so the grid repaints those rows instead of refetching the table; [deletedRowIds]
 * are ids we asked about that no longer resolve. [collaborative] is the server's
 * own "is anyone else involved with this list" flag — when it is false there is
 * nothing to poll for and the client must stop.
 */
data class ListFreshness(
    val changed: List<ListRow> = emptyList(),
    val deletedRowIds: List<String> = emptyList(),
    val presence: List<ListPresence> = emptyList(),
    val collaborative: Boolean = false,
) {
    /** Whether this poll actually brought news — decides the next poll interval. */
    val hasChanges: Boolean get() = changed.isNotEmpty() || deletedRowIds.isNotEmpty()
}
