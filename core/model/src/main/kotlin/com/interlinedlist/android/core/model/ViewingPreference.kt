package com.interlinedlist.android.core.model

/**
 * Which messages the Home feed shows — the `viewingPreference` field of
 * `GET /api/user`, written back with `PATCH /api/user/update`.
 *
 * The wire values are not in the OpenAPI spec (the field is typed as a bare
 * `string`); they come from the server's own 400 on a bogus value:
 * `viewingPreference must be one of: my_messages, all_messages, followers_only,
 * following_only`. Nothing else is accepted.
 *
 * The **server** applies this preference when it builds the feed:
 * `GET /api/messages` takes only `limit`, `offset`, `onlyMine` and `tag`
 * (`/help/api/messages`), and search is documented as "scoped to your feed
 * visibility (honors your `viewingPreference`)". So a client changes the feed by
 * saving the preference and reloading, not by sending a filter parameter.
 *
 * [fromWire] is deliberately tolerant — casing, separators and the obvious
 * shorthands all resolve — so an unexpected spelling degrades to the right
 * selection instead of silently resetting the user's feed.
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
        /** The API default for a new account, and the fallback for an unknown value. */
        val DEFAULT = ALL

        /** Parses a wire value, returning null when it matches no known option. */
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

        /** Parses a wire value, falling back to [DEFAULT] when missing or unknown. */
        fun fromWireOrDefault(value: String?): ViewingPreference = fromWire(value) ?: DEFAULT
    }
}
