package com.interlinedlist.android.feature.notifications.push

/**
 * The `environment` value sent with a device-token registration.
 *
 * The field is optional — the server infers it when omitted — but we always send it so
 * a token minted by a developer build can never be filed as a production device and
 * start receiving real pushes for an account.
 *
 * It is derived from the BUILD TYPE rather than hard-coded, and specifically from
 * whether the installed application is debuggable (`ApplicationInfo.FLAG_DEBUGGABLE`)
 * rather than from a `BuildConfig.DEBUG` constant. Two reasons:
 * 1. `BuildConfig` in a library module reflects that library's own variant, not the
 *    variant of the app that embeds it, so it is the wrong signal here (and generating
 *    one would mean enabling `buildConfig` just for a single boolean);
 * 2. the debuggable flag is the exact debug/release distinction the push providers
 *    themselves draw between their sandbox and production gateways.
 */
enum class PushEnvironment(val apiValue: String) {
    /** Developer builds — keeps debug tokens off the production gateway. */
    SANDBOX("sandbox"),

    /** Release builds. */
    PRODUCTION("production"),
    ;

    companion object {
        /** Maps the installed app's debuggable flag onto the wire value. */
        fun fromDebuggable(debuggable: Boolean): PushEnvironment =
            if (debuggable) SANDBOX else PRODUCTION
    }
}
