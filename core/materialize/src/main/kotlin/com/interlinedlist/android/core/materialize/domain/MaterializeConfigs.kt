package com.interlinedlist.android.core.materialize.domain

/**
 * What the preview/edit step may change about the list a conversion produces.
 *
 * [title] is mandatory: the server validates it right after the target and
 * answers `{"error":"A list title is required","code":"bad_request"}` without
 * it, so requiring it here turns a guaranteed round-trip failure into something
 * that will not compile. Everything else is optional; omitting a field lets the
 * server derive its own default from the source (messages default to
 * Content / Author / Posted / Links / Tags, a document defaults to one row per
 * heading and bullet).
 */
data class ListConfig(
    val title: String,
    val description: String? = null,
    val isPublic: Boolean? = null,
    /** Column definitions. Null leaves the server's derived columns alone. */
    val fields: List<MaterializeColumn>? = null,
    /**
     * Seed the new list with rows derived from the source. The server defaults
     * to true; set false for an empty, columns-only list.
     */
    val includeData: Boolean? = null,
) {
    init { require(title.isNotBlank()) { "A list title is required" } }
}

/**
 * One column of the list a conversion produces.
 *
 * [sourceKey] is what makes this safe: it names the source attribute the column
 * takes its values from, and the server re-derives every cell from that key. It
 * does not accept client cell values. A **null** [sourceKey] means a user-added
 * empty column, and is sent as an explicit JSON `null` rather than being omitted.
 */
data class MaterializeColumn(
    val propertyKey: String,
    val propertyName: String,
    val propertyType: ListColumnType,
    val sourceKey: String? = null,
    val isRequired: Boolean? = null,
    /** Allowed values; required by [ListColumnType.SELECT] and [ListColumnType.MULTISELECT]. */
    val options: List<String>? = null,
)

/**
 * The twelve column types the list schema accepts. The server rejects anything
 * else with `bad_request`, so the picker in the preview step chooses from here
 * rather than from free text.
 */
enum class ListColumnType(val apiValue: String) {
    TEXT("text"),
    TEXTAREA("textarea"),
    NUMBER("number"),
    BOOLEAN("boolean"),
    DATE("date"),
    DATETIME("datetime"),
    EMAIL("email"),
    URL("url"),
    TEL("tel"),
    SELECT("select"),
    MULTISELECT("multiselect"),
    PRIORITY("priority");

    companion object {
        /** Maps a wire string (or null) back to a type, or null when unknown. */
        fun fromApiValue(value: String?): ListColumnType? =
            entries.firstOrNull { it.apiValue == value }
    }
}

/**
 * What the preview/edit step may change about the document a conversion
 * produces. [listStyle] and [rowDataStyle] only bite when the source is a list
 * or a set of rows.
 *
 * Unlike [ListConfig.title], a document title is documented as optional and has
 * not been observed to be required, so it stays nullable and the server derives
 * one from the source when it is omitted.
 */
data class DocConfig(
    val title: String? = null,
    /** Folder path / file name for the new document. */
    val relativePath: String? = null,
    val isPublic: Boolean? = null,
    val listStyle: DocumentListStyle? = null,
    val rowDataStyle: RowDataStyle? = null,
)

/** How list/row sources render as document bullets. */
enum class DocumentListStyle(val apiValue: String) {
    NUMBERED("numbered"),
    BULLETED("bulleted"),
}

/** How each row's fields are laid out under its headline. */
enum class RowDataStyle(val apiValue: String) {
    INLINE("inline"),
    SUB_ITEMS("sub-items"),
}

/**
 * What the preview/edit step may change about the **draft** the message
 * destination returns. Nothing here is posted: `publiclyVisible`, `tags` and
 * `scheduledAt` are accepted and handed straight back so the composer can apply
 * them, and every posting rule is enforced later by `POST /api/messages`.
 */
data class MessageDraftConfig(
    /** The edited body. Omitted, the server derives one from the source. */
    val content: String? = null,
    /** Sizes the draft: the tightest selected channel wins over the account limit. */
    val crossPostTargets: List<CrossPostChannel>? = null,
    /**
     * Permission to split an over-length body into a thread. The server rejects
     * the request rather than splitting someone's post unasked.
     */
    val allowThread: Boolean? = null,
    val publiclyVisible: Boolean? = null,
    val tags: List<String>? = null,
    /** ISO-8601 instant, passed through to the composer. */
    val scheduledAt: String? = null,
)

/**
 * A cross-post channel, used only to size the draft's `charLimit`.
 *
 * The published reference names these as labels ("Bluesky, Mastodon, LinkedIn,
 * X/Twitter") without pinning the wire spelling; these values match the
 * `platform` field the API documents on a created message's `crossPosts` array,
 * which is the only place the spelling is stated. If the server turns out to
 * want display labels, only the draft's character budget is affected — nothing
 * is created by this destination.
 */
enum class CrossPostChannel(val apiValue: String) {
    BLUESKY("bluesky"),
    MASTODON("mastodon"),
    LINKEDIN("linkedin"),
    TWITTER("twitter"),
}
