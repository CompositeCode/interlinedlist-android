package com.interlinedlist.android.feature.profile.domain

/**
 * Bounds and server defaults for the numeric message preferences, plus the fallbacks
 * the Settings UI shows when `GET /api/user` omits a preference.
 *
 * The ranges are enforced client-side so a stepper or a typed value can never PATCH
 * nonsense (0, a negative page size, a nine-digit character limit). The server has
 * the final say — it may refuse a value we consider valid — so callers must still
 * handle a rejected save by restoring the previous value.
 */
object SettingsBounds {

    /**
     * How many messages the feed loads at a time. The help centre states the
     * supported range outright: "Messages per page: How many messages to load at
     * once (10 to 30)".
     */
    val MESSAGES_PER_PAGE: IntRange = 10..30

    /**
     * The account's message character limit. Neither the help centre nor the OpenAPI
     * spec publishes a range for this one (only the 666 default), so the bounds are
     * deliberately wide: low enough to allow a deliberately terse limit, high enough
     * to clear any plausible server cap, and tight enough to reject nonsense. A value
     * in this range that the server still refuses surfaces as a failed save.
     */
    val MAX_MESSAGE_LENGTH: IntRange = 10..10_000

    /** Stepper increment for [MAX_MESSAGE_LENGTH]; the field is there for big jumps. */
    const val MAX_MESSAGE_LENGTH_STEP: Int = 10

    /** The character limit a fresh account gets (help centre: "default 666"). */
    const val DEFAULT_MAX_MESSAGE_LENGTH: Int = 666

    /** The page size a fresh account gets (observed live on a real account). */
    const val DEFAULT_MESSAGES_PER_PAGE: Int = 20

    /** New messages start public unless the account says otherwise. */
    const val DEFAULT_PUBLICLY_VISIBLE: Boolean = true

    /** The composer's gear options stay hidden unless the account opts in. */
    const val DEFAULT_SHOW_ADVANCED_POST_SETTINGS: Boolean = false
}

/**
 * The character limit to show, falling back to the server default when the account
 * has no stored value. The preference fields are nullable because public profiles
 * omit them; the Settings UI still has to render a concrete number.
 */
val UserSettings.maxMessageLengthOrDefault: Int
    get() = maxMessageLength ?: SettingsBounds.DEFAULT_MAX_MESSAGE_LENGTH

/** The feed page size to show, falling back to the server default. */
val UserSettings.messagesPerPageOrDefault: Int
    get() = messagesPerPage ?: SettingsBounds.DEFAULT_MESSAGES_PER_PAGE

/** The composer's starting visibility, falling back to the server default. */
val UserSettings.defaultPubliclyVisibleOrDefault: Boolean
    get() = defaultPubliclyVisible ?: SettingsBounds.DEFAULT_PUBLICLY_VISIBLE

/** Whether the composer's advanced options show, falling back to the server default. */
val UserSettings.showAdvancedPostSettingsOrDefault: Boolean
    get() = showAdvancedPostSettings ?: SettingsBounds.DEFAULT_SHOW_ADVANCED_POST_SETTINGS
