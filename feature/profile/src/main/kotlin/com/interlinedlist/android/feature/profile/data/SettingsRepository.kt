package com.interlinedlist.android.feature.profile.data

import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.datastore.ThemeMode
import com.interlinedlist.android.feature.profile.domain.UserSettings
import com.interlinedlist.android.feature.profile.domain.UserSettingsUpdate
import kotlinx.coroutines.flow.Flow

/**
 * The current user's preferences, read from `GET /api/user` and written with
 * `PATCH /api/user/update`.
 *
 * Kept separate from [ProfileRepository] because the consumers differ: Settings
 * screens and the messages feed care about preference values, not about the profile
 * cache. Preferences are a small always-fresh surface, so they are held in memory
 * (process-scoped) rather than in Room — [observeSettings] replays the latest known
 * value to every collector and re-emits after each successful refresh or update.
 */
interface SettingsRepository {

    /** The last known settings, or null until the first successful [refresh]. */
    fun observeSettings(): Flow<UserSettings?>

    /**
     * Re-reads the settings from `GET /api/user` and publishes them to [observeSettings].
     *
     * This is also the app's **theme sync point**: after the account's values land, the
     * device's appearance is reconciled against the account's `theme` by
     * [com.interlinedlist.android.feature.profile.domain.reconcileTheme] — adopting the
     * account's choice when this device has nothing unsynced (so a fresh install lands
     * in the theme picked on the web), and pushing a choice made offline when it does.
     * A failed push leaves the choice pending for the next refresh; it is never lost.
     */
    suspend fun refresh(): ApiResult<UserSettings>

    /**
     * The appearance in force on this device — the local store, which is the source of
     * truth for what the app renders whether or not the account is reachable.
     *
     * Read from here rather than from [UserSettings.theme]: the account field is the
     * last value the *server* knew, which an offline change deliberately outruns.
     */
    fun observeThemeMode(): Flow<ThemeMode>

    /**
     * Applies a theme chosen on this device.
     *
     * The local store is written **first and unconditionally**, so the app re-themes at
     * once and keeps the choice with no connection; the account is then PATCHed with
     * `theme` alone. A failure is reported so the UI can say the change has not synced
     * yet, but it does **not** roll the choice back — the next [refresh] pushes it.
     */
    suspend fun setThemeMode(mode: ThemeMode): ApiResult<UserSettings>

    /**
     * Applies a partial [update] via `PATCH /api/user/update`. Fields left null in
     * [update] are omitted from the request, so other preferences are untouched.
     */
    suspend fun update(update: UserSettingsUpdate): ApiResult<UserSettings>
}
