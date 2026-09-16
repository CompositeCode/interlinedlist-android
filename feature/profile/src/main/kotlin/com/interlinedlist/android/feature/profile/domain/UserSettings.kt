package com.interlinedlist.android.feature.profile.domain

/**
 * Which messages the Home feed shows — the `viewingPreference` field of
 * `GET /api/user` / `PATCH /api/user/update`.
 *
 * The OpenAPI spec types the field as a bare `string` (it is a plain Prisma column,
 * not an enum), so the wire values below come from the live API instead: `GET /api/user`
 * returns `"all_messages"`, and PATCHing a bogus value answers with the server's own
 * allow-list — `viewingPreference must be one of: my_messages, all_messages,
 * followers_only, following_only`.
 *
 * [fromWire] stays deliberately tolerant — any casing plus the obvious shorthands —
 * so an unexpected spelling degrades to the right selection rather than silently
 * resetting the user's feed.
 */
enum class ViewingPreference(val wire: String) {
    /** Your messages plus all public messages. */
    ALL("all_messages"),

    /** Only your own messages. */
    MINE("my_messages"),

    /** Messages from people you follow, plus your own. */
    FOLLOWING("following_only"),

    /** Messages from people who follow you, plus your own. */
    FOLLOWERS("followers_only"),
    ;

    companion object {
        /** What the API defaults to for a new account, and our fallback for an unknown value. */
        val DEFAULT = ALL

        /**
         * Parses a wire value, returning null when it matches no known option. Casing
         * and separators are ignored, so the canonical values (listed first below) and
         * the obvious shorthands both resolve.
         */
        fun fromWire(value: String?): ViewingPreference? {
            val normalised = value?.lowercase()?.filter { it.isLetter() } ?: return null
            return when (normalised) {
                "allmessages", "all", "everyone" -> ALL
                "mymessages", "mine", "my", "onlymine", "me" -> MINE
                "followingonly", "following" -> FOLLOWING
                "followersonly", "followers" -> FOLLOWERS
                else -> null
            }
        }

        /** Parses a wire value, falling back to [DEFAULT] when it is missing or unknown. */
        fun fromWireOrDefault(value: String?): ViewingPreference = fromWire(value) ?: DEFAULT
    }
}

/**
 * The current user's preference fields as returned by `GET /api/user`.
 *
 * Every field the Settings surface can show is modelled up front so the remaining
 * settings groups only have to add UI. Fields are nullable where the API may omit
 * them; [viewingPreference] and [showPreviews] carry the server defaults because the
 * feed consumes them directly and must never be left undecided.
 *
 * Profile fields (display name, bio, avatar) intentionally live on [ProfileUser] —
 * they are edited by the edit-profile screen, not by Settings.
 */
data class UserSettings(
    val theme: String? = null,
    val maxMessageLength: Int? = null,
    val defaultPubliclyVisible: Boolean? = null,
    val messagesPerPage: Int? = null,
    val viewingPreference: ViewingPreference = ViewingPreference.DEFAULT,
    val showPreviews: Boolean = true,
    val showAdvancedPostSettings: Boolean? = null,
    /** The profile location, present only when the account has one set. */
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isPrivateAccount: Boolean? = null,
    val githubDefaultRepo: String? = null,
    val notificationTrayLimit: Int? = null,
)

/**
 * A partial update for `PATCH /api/user/update`: every field the endpoint accepts,
 * all optional. A null field means "leave it alone" — it is omitted from the request
 * body entirely, so changing one preference can never clobber another.
 *
 * A consequence of that rule is that a nullable server field cannot be *cleared* by
 * setting it to null here — null already means "leave it alone". A field that has to
 * be clearable therefore models the clear explicitly, which is what [location] does
 * for the coordinates (see [LocationUpdate]).
 */
data class UserSettingsUpdate(
    val displayName: String? = null,
    val bio: String? = null,
    val avatar: String? = null,
    val theme: String? = null,
    val maxMessageLength: Int? = null,
    val defaultPubliclyVisible: Boolean? = null,
    val messagesPerPage: Int? = null,
    val viewingPreference: ViewingPreference? = null,
    val showPreviews: Boolean? = null,
    val showAdvancedPostSettings: Boolean? = null,
    /** Set or clear the profile location; null leaves the stored coordinates alone. */
    val location: LocationUpdate? = null,
    val isPrivateAccount: Boolean? = null,
    val githubDefaultRepo: String? = null,
    val notificationTrayLimit: Int? = null,
)
