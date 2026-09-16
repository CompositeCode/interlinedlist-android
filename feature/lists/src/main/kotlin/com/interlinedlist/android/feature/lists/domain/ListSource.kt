package com.interlinedlist.android.feature.lists.domain

/**
 * Where a list's rows come from. The API stores this as a short string on the
 * list row (`source`), observed as `"local"` for ordinary lists; `"github"` marks
 * a repository-backed list that `POST /api/lists/{id}/refresh` re-syncs.
 *
 * Modelled as an enum so callers pass a value the API recognises instead of a
 * free-form string.
 */
enum class ListSource(val wire: String) {
    /** A normal list whose rows are edited in the app. */
    LOCAL("local"),

    /** A list mirrored from a GitHub repository. */
    GITHUB("github"),
    ;

    companion object {
        /** Maps the API's `source` string to a [ListSource], defaulting to [LOCAL]. */
        fun fromWire(raw: String?): ListSource =
            entries.firstOrNull { it.wire.equals(raw?.trim(), ignoreCase = true) } ?: LOCAL
    }
}
