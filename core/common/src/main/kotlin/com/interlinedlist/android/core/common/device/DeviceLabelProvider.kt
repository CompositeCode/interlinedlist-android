package com.interlinedlist.android.core.common.device

/**
 * The one human-readable name this install reports for itself.
 *
 * Two independent server-side registries show the user their devices, and they must
 * agree or the same phone reads as two different machines:
 * - `POST /api/auth/sync-token` sends it as `deviceLabel` → Settings → **Sessions**;
 * - `POST /api/user/app-settings/{appKey}/devices` sends it as `deviceName` →
 *   Settings → **Applications**.
 *
 * Declared in `:core:common` for the same reason as
 * [com.interlinedlist.android.core.common.session.SessionTokenProvider]: the two
 * callers live in different modules (`:feature:auth` and `:core:appsettings`) and
 * neither may depend on the other, so the contract sits in the module both already
 * depend on while the Android implementation is contributed once, elsewhere.
 */
interface DeviceLabelProvider {

    /**
     * A label such as `InterlinedList Android · Pixel 8`. Never blank, and never
     * longer than [MAX_LENGTH] characters — the device registry rejects a
     * `deviceName` outside 1..120 characters with a 400.
     */
    val deviceLabel: String

    companion object {
        /** The server's `deviceName` limit (1..120 characters, trimmed). */
        const val MAX_LENGTH = 120
    }
}
