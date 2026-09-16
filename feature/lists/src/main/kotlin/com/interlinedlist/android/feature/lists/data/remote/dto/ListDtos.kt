package com.interlinedlist.android.feature.lists.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Wire models for the Lists API. Field names follow the InterlinedList REST
 * contract; the shared [kotlinx.serialization.json.Json] is configured with
 * `ignoreUnknownKeys`, so extra server fields are tolerated and only the columns
 * we render need to be declared here.
 *
 * The list `schema` and each row's `data` are intentionally left as raw
 * [JsonElement]: they are user-defined and dynamic, so they are interpreted by
 * the mappers ([com.interlinedlist.android.feature.lists.data.SchemaMapper] /
 * [com.interlinedlist.android.feature.lists.data.RowMapper]) rather than by fixed
 * @Serializable shapes.
 */

/**
 * A list envelope as returned by index and detail endpoints.
 *
 * `GET /api/lists` returns each row with its tree links: `parentId` plus a nested
 * `parent` object (null at the root) and a `children` array. The nested [parent]
 * is modelled recursively so the breadcrumb can consume whatever depth the server
 * chooses to send and only fetch the levels it did not.
 */
@Serializable
data class ListDto(
    val id: String,
    val title: String = "",
    val description: String? = null,
    val itemCount: Int? = null,
    val rowCount: Int? = null,
    val count: Int? = null,
    val folderId: String? = null,
    val isPublic: Boolean = false,
    val updatedAt: String? = null,
    /** The list this one hangs under; `null` for a root list. */
    val parentId: String? = null,
    /** The parent as an object, when the server inlines it. */
    val parent: ListDto? = null,
    /** Direct children, when the server inlines them. */
    val children: List<ListDto>? = null,
    /** The message this list was created from, if any. */
    val messageId: String? = null,
    /** Free-form pass-through object; kept raw so no keys are lost. */
    val metadata: JsonElement? = null,
    /** Where the rows come from — `"local"`, `"github"`, … (see `ListSource`). */
    val source: String? = null,
    /** `"owner/repo"` backing a GitHub list; null on a local one. */
    val githubRepo: String? = null,
    /**
     * Whether the backing **repository** is private on GitHub. Re-read on every
     * sync, and absent (null) until a list has synced at least once — which is
     * why it stays nullable rather than defaulting to `false`.
     */
    val githubRepoPrivate: Boolean? = null,
    // Detail responses may inline the schema; the mapper handles either shape.
    val schema: JsonElement? = null,
    /**
     * Column definitions inlined on a detail response. A GitHub-backed list has
     * no stored schema — `GET /api/lists/{id}` returns its fixed nine issue
     * columns here — so this is the only place that list's schema can be read.
     */
    val properties: JsonElement? = null,
)

/** Pagination block shared by list endpoints. */
@Serializable
data class PaginationDto(
    val total: Int = 0,
    val limit: Int = 0,
    val offset: Int = 0,
    val hasMore: Boolean = false,
)

/**
 * Envelope for `GET /api/lists` and `GET /api/lists/search`.
 * The payload may either be `{ data: [...], pagination: {...} }` or (for some
 * builds) `{ lists: [...] }`; both list keys are accepted.
 */
@Serializable
data class ListsResponse(
    val data: List<ListDto>? = null,
    val lists: List<ListDto>? = null,
    val pagination: PaginationDto? = null,
) {
    val items: List<ListDto> get() = data ?: lists ?: emptyList()
}

/** Envelope for `GET /api/lists/{id}`; the list may be wrapped or bare. */
@Serializable
data class ListEnvelope(
    val list: ListDto? = null,
    val data: ListDto? = null,
)

/**
 * Body for `POST /api/lists`.
 *
 * Every field beyond [title] is optional and defaults to `null` so it is
 * **omitted** from the JSON rather than sent as an explicit `null` — a caller
 * that only wants a title still produces `{"title":"…"}`.
 *
 * - [schema] is the List Schema DSL **object** (`{ name, description?, fields[] }`),
 *   per the help centre's Lists API reference — not a serialised string (that form
 *   belongs to `PUT /api/lists/{id}/schema`).
 * - [initialRows] are starter rows; each is the same flat, column-keyed object
 *   that `POST /api/lists/{id}/data` sends under `data`. The OpenAPI spec types
 *   this field as a bare `string` and the help centre does not document it at all,
 *   so the element shape follows the list-data API rather than the generated spec.
 * - [metadata] is undocumented and free-form, so it is kept as [JsonObject] and
 *   passed through with every key intact.
 * - [source] is the low-cardinality list source string — `"local"` or `"github"`
 *   (see [com.interlinedlist.android.feature.lists.domain.ListSource]).
 * - [githubRepo] (`"owner/repo"`) is **required** when [source] is `"github"`: the
 *   server answers `400 githubRepo is required for GitHub-backed lists (format:
 *   owner/repo)` without it. [githubSource] names which part of the repository the
 *   rows mirror and is optional; issues are the documented mapping
 *   ([com.interlinedlist.android.feature.lists.domain.GITHUB_SOURCE_ISSUES]).
 * - [folderId] is a real column on a list, but neither the OpenAPI create schema
 *   nor the help centre lists it as a *create* field: filing a list into a folder
 *   is documented on `PUT /api/lists/{id}`. It is sent when supplied; a caller that
 *   must be certain should follow up with an update.
 */
@Serializable
data class CreateListRequest(
    val title: String,
    val description: String? = null,
    val schema: JsonObject? = null,
    val isPublic: Boolean = false,
    val parentId: String? = null,
    val folderId: String? = null,
    val messageId: String? = null,
    val initialRows: List<JsonObject>? = null,
    val metadata: JsonObject? = null,
    val source: String? = null,
    val githubRepo: String? = null,
    val githubSource: String? = null,
)

/**
 * Body for `PUT /api/lists/{id}` — partial metadata updates.
 *
 * [parentId] re-parents the list. It is the one thing a GitHub-backed list's
 * schema editor may change, since those lists' columns are fixed by GitHub.
 */
@Serializable
data class UpdateListRequest(
    val title: String? = null,
    val description: String? = null,
    val folderId: String? = null,
    val isPublic: Boolean? = null,
    val parentId: String? = null,
)
