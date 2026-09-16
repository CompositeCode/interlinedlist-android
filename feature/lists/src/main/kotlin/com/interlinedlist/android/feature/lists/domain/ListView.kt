package com.interlinedlist.android.feature.lists.domain

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A saved view on a list: a named, reusable way of looking at the same rows.
 *
 * `GET /api/lists/{id}/views` returns every [ListViewScope.SHARED] view on the
 * list plus the current user's own [ListViewScope.PERSONAL] views, so a shared
 * view may well belong to somebody else — see [isOwnedBy], which gates renaming
 * and deleting. When someone else's shared view does not suit, it can be forked
 * into a personal copy instead.
 */
data class ListView(
    val id: String,
    val listId: String,
    val userId: String?,
    val name: String,
    val scope: ListViewScope,
    val config: ListViewConfig,
    val isDefault: Boolean,
    val position: Int,
) {
    val isShared: Boolean get() = scope == ListViewScope.SHARED

    /**
     * Whether [currentUserId] may rename or delete this view. When both ids are
     * known it is a straight comparison; with an unknown id we fall back to the
     * scope, because the endpoint only ever returns the caller's own personal
     * views — a shared view of unknown ownership is treated as somebody else's.
     */
    fun isOwnedBy(currentUserId: String?): Boolean = when {
        userId != null && currentUserId != null -> userId == currentUserId
        else -> scope == ListViewScope.PERSONAL
    }
}

/**
 * Who a saved view belongs to. The API accepts exactly `"personal"` or
 * `"shared"` and rejects anything else with a 400, so [fromApi] returns `null`
 * for an unrecognised value rather than guessing — callers validate before they
 * spend a request.
 */
enum class ListViewScope(val apiValue: String, val label: String) {
    PERSONAL("personal", "Personal"),
    SHARED("shared", "Shared"),
    ;

    companion object {
        fun fromApi(raw: String?): ListViewScope? =
            entries.firstOrNull { it.apiValue == raw?.trim()?.lowercase() }
    }
}

/**
 * A saved view's `config` blob.
 *
 * The server silently drops `config` values it does not recognise instead of
 * rejecting them, so what it stores is authoritative and every write must be
 * reconciled against the response. The whole object is kept in [raw] so keys
 * this client does not model survive a round-trip untouched — the client must
 * not compound the server's own lossiness — while [mode], [density] and
 * [filters] project the parts we do understand.
 */
data class ListViewConfig(val raw: JsonObject) {

    /** The stored display mode, falling back to [ListViewMode.RECORDS]. */
    val mode: ListViewMode get() = ListViewMode.fromApi(string(KEY_MODE)) ?: ListViewMode.RECORDS

    /** The raw stored mode, which may be a value this client does not model. */
    val storedMode: String? get() = string(KEY_MODE)

    val density: ListViewDensity
        get() = ListViewDensity.fromApi(string(KEY_DENSITY)) ?: ListViewDensity.COMFORTABLE

    /** Saved filters, passed through verbatim — their shape is the server's business. */
    val filters: JsonArray get() = raw[KEY_FILTERS] as? JsonArray ?: EMPTY_FILTERS

    /** Copies the config with one key replaced, leaving every other key intact. */
    fun with(key: String, value: JsonElement): ListViewConfig =
        ListViewConfig(JsonObject(raw + (key to value)))

    private fun string(key: String): String? =
        (raw[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    companion object {
        const val KEY_MODE = "mode"
        const val KEY_DENSITY = "density"
        const val KEY_FILTERS = "filters"

        private val EMPTY_FILTERS = JsonArray(emptyList())

        /** The config the server applies when none is supplied (verified live). */
        val DEFAULT = ListViewConfig(
            JsonObject(
                mapOf(
                    KEY_MODE to JsonPrimitive(ListViewMode.RECORDS.apiValue),
                    KEY_DENSITY to JsonPrimitive(ListViewDensity.COMFORTABLE.apiValue),
                    KEY_FILTERS to EMPTY_FILTERS,
                ),
            ),
        )

        /** Parses a server config, defaulting when the view carries none. */
        fun fromJson(raw: JsonObject?): ListViewConfig = raw?.let(::ListViewConfig) ?: DEFAULT
    }
}

/**
 * How a view renders its rows.
 *
 * Only `records` exists: probing the live API showed every other candidate
 * (`cards`, `grid`, `table`, `erd`, …) silently stored as `records`. Unknown
 * stored modes still survive in [ListViewConfig.raw] and are readable through
 * [ListViewConfig.storedMode].
 */
enum class ListViewMode(val apiValue: String, val label: String) {
    RECORDS("records", "Records"),
    ;

    companion object {
        fun fromApi(raw: String?): ListViewMode? =
            entries.firstOrNull { it.apiValue == raw?.trim()?.lowercase() }
    }
}

/** Row spacing a view asks for. The server accepts these two values only. */
enum class ListViewDensity(val apiValue: String, val label: String) {
    COMFORTABLE("comfortable", "Comfortable"),
    COMPACT("compact", "Compact"),
    ;

    companion object {
        fun fromApi(raw: String?): ListViewDensity? =
            entries.firstOrNull { it.apiValue == raw?.trim()?.lowercase() }
    }
}
