package com.interlinedlist.android.feature.profile.domain

import com.interlinedlist.android.core.datastore.ThemeMode

/**
 * The account's wire value for this appearance — the `theme` field of `GET /api/user`
 * and `PATCH /api/user/update`.
 *
 * The values come from the help centre, because the API will not tell us: `theme` is a
 * plain Prisma string column with **no server-side validation at all** (a PATCH of
 * `"sepia"`, or of `""`, is answered 200 and stored verbatim), so unlike
 * `viewingPreference` there is no allow-list to coax out of a 400. `/help/settings`
 * publishes the web's own vocabulary instead: "Theme: Light, dark, or system (follows
 * your device preference)". Live `GET /api/user` returns it lower-case (`"light"`).
 *
 * Two consequences worth stating:
 * - The account **does** have a "follows the device" option, so a local [ThemeMode.SYSTEM]
 *   maps straight onto `"system"`. It is never coerced into light or dark, and a user
 *   who follows their phone's theme keeps doing so after a sync.
 * - Because the server validates nothing, this app must be the conservative side: it
 *   sends only these three values and refuses to adopt anything else.
 */
val ThemeMode.wire: String
    get() = when (this) {
        ThemeMode.SYSTEM -> "system"
        ThemeMode.LIGHT -> "light"
        ThemeMode.DARK -> "dark"
    }

/**
 * Parses an account `theme` value, returning null when it is missing or is something
 * this app cannot render.
 *
 * Deliberately tolerant about casing and whitespace, and about `"auto"` as the obvious
 * synonym for "follow the device" — but deliberately *intolerant* about everything
 * else. Since the server stores any string, a null here is a real possibility, and
 * "leave the device's theme alone" is a far better answer to an unknown value than
 * guessing at light.
 */
fun themeModeFromWire(value: String?): ThemeMode? =
    when (value?.trim()?.lowercase()) {
        "system", "auto" -> ThemeMode.SYSTEM
        "light" -> ThemeMode.LIGHT
        "dark" -> ThemeMode.DARK
        else -> null
    }

/** What reconciling the device's theme against the account's `theme` should do. */
sealed interface ThemeReconciliation {

    /** The two already agree, or neither side has anything to offer. Do nothing. */
    data object InSync : ThemeReconciliation

    /** Store [mode] as the device's theme and record the account as holding it. */
    data class AdoptAccount(val mode: ThemeMode) : ThemeReconciliation

    /** PATCH [mode] to the account; the device keeps it either way. */
    data class PushLocal(val mode: ThemeMode) : ThemeReconciliation
}

/**
 * Decides which side wins when the device's theme and the account's disagree.
 *
 * **The rule: an unsynced local choice wins; otherwise the account wins.**
 *
 * Neither side carries a modification timestamp, so "newest wins" is not available.
 * What *is* available is whether this device is holding a choice that never reached
 * the account ([hasUnsyncedLocalChange], set by `ThemeSettingsStore.setThemeMode` and
 * cleared only when the account confirms the value). That flag is exactly the
 * happened-after evidence the rule needs:
 *
 * - **Unsynced local change → push it.** The account's value cannot be newer: it is
 *   whatever was there *before* the user made this change, because the change never
 *   got out. Adopting the account here would silently undo an edit the user made
 *   offline — the failure mode the issue calls out. The one exception is when the
 *   account already holds the same mode, where pushing would be a pointless write, so
 *   the flag is simply cleared instead.
 * - **No unsynced local change → adopt the account.** Everything this device knows
 *   has already been pushed, so any difference came from somewhere else (the web, or
 *   another device) and is newer. This is also what makes a fresh install land in the
 *   theme chosen on the web: the local default is untouched, so the account wins.
 * - **Nothing usable on the account → leave both alone.** `theme` may be absent, and
 *   because the server validates nothing it may hold a value this app cannot render.
 *   Neither is worth acting on: we do not adopt what we cannot show, and we do not
 *   overwrite it with a default the user never chose.
 *
 * @param local the appearance currently in force on this device.
 * @param hasUnsyncedLocalChange whether [local] is a choice the account has not confirmed.
 * @param accountTheme the raw `theme` value from `GET /api/user`.
 */
fun reconcileTheme(
    local: ThemeMode,
    hasUnsyncedLocalChange: Boolean,
    accountTheme: String?,
): ThemeReconciliation {
    val account = themeModeFromWire(accountTheme)
    return when {
        hasUnsyncedLocalChange && account == local -> ThemeReconciliation.AdoptAccount(local)
        hasUnsyncedLocalChange -> ThemeReconciliation.PushLocal(local)
        account == null || account == local -> ThemeReconciliation.InSync
        else -> ThemeReconciliation.AdoptAccount(account)
    }
}
