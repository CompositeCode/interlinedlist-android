package com.interlinedlist.android.feature.profile.data

import com.interlinedlist.android.core.common.result.ApiResult
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

    /** Re-reads the settings from `GET /api/user` and publishes them to [observeSettings]. */
    suspend fun refresh(): ApiResult<UserSettings>

    /**
     * Applies a partial [update] via `PATCH /api/user/update`. Fields left null in
     * [update] are omitted from the request, so other preferences are untouched.
     */
    suspend fun update(update: UserSettingsUpdate): ApiResult<UserSettings>
}
