package com.interlinedlist.android.feature.profile.data

import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.core.datastore.ThemeMode
import com.interlinedlist.android.core.datastore.ThemeSettingsStore
import com.interlinedlist.android.core.network.error.safeApiCall
import com.interlinedlist.android.core.network.preferences.NotificationTrayLimitStore
import com.interlinedlist.android.feature.profile.data.mapper.toRequest
import com.interlinedlist.android.feature.profile.data.mapper.toUserSettings
import com.interlinedlist.android.feature.profile.data.remote.ProfileApi
import com.interlinedlist.android.feature.profile.domain.ThemeReconciliation
import com.interlinedlist.android.feature.profile.domain.UserSettings
import com.interlinedlist.android.feature.profile.domain.UserSettingsUpdate
import com.interlinedlist.android.feature.profile.domain.reconcileTheme
import com.interlinedlist.android.feature.profile.domain.wire
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Network-backed settings with a process-scoped in-memory cache: every successful
 * read or write publishes the new value to [observeSettings], so the Settings screen
 * and the feed see the same preferences without either re-fetching.
 *
 * `notificationTrayLimit` is additionally forwarded to [NotificationTrayLimitStore] in
 * `:core:network`, because `:feature:notifications` sizes its list and its system-tray
 * group by that preference and no feature module here may depend on another. Without
 * the forward, a limit changed in Settings would not take effect until the process
 * restarted.
 *
 * `theme` is the one preference that is **not** read straight off the account: the
 * device's [ThemeSettingsStore] is what the app renders from, so the appearance holds
 * with no connection, and [refresh] reconciles the two. See
 * [com.interlinedlist.android.feature.profile.domain.reconcileTheme] for the rule that
 * decides which side wins.
 */
@Singleton
class DefaultSettingsRepository @Inject constructor(
    private val api: ProfileApi,
    private val trayLimitStore: NotificationTrayLimitStore,
    private val themeStore: ThemeSettingsStore,
    private val json: Json,
    private val dispatchers: DispatcherProvider,
) : SettingsRepository {

    private val cached = MutableStateFlow<UserSettings?>(null)

    override fun observeSettings(): Flow<UserSettings?> = cached.asStateFlow()

    override fun observeThemeMode(): Flow<ThemeMode> = themeStore.themeMode

    override suspend fun refresh(): ApiResult<UserSettings> = withContext(dispatchers.io) {
        when (val result = fetch()) {
            is ApiResult.Success -> ApiResult.Success(reconcileTheme(result.data))
            is ApiResult.Failure -> result
        }
    }

    override suspend fun setThemeMode(mode: ThemeMode): ApiResult<UserSettings> =
        withContext(dispatchers.io) {
            // Local first, and regardless of what the network does next: the app must
            // re-theme immediately and keep the choice while offline. The store marks
            // it unsynced, which is what makes the next refresh push it rather than
            // overwrite it with the account's stale value.
            themeStore.setThemeMode(mode)
            patch(UserSettingsUpdate(theme = mode.wire)).also { result ->
                if (result is ApiResult.Success) themeStore.setSyncedThemeMode(mode)
            }
        }

    override suspend fun update(update: UserSettingsUpdate): ApiResult<UserSettings> =
        withContext(dispatchers.io) { patch(update) }

    /** `GET /api/user` into the cache, with no theme reconciliation. */
    private suspend fun fetch(): ApiResult<UserSettings> =
        when (val result = safeApiCall(json) { api.getCurrentUser().userOrSelf }) {
            is ApiResult.Success -> result.data
                ?.let { ApiResult.Success(publish(it.toUserSettings())) }
                ?: ApiResult.Failure(AppError.Unknown("No user in response"))
            is ApiResult.Failure -> result
        }

    /**
     * `PATCH /api/user/update` into the cache. Falls back to [fetch] — not [refresh] —
     * when the endpoint echoes a thin body, so a push issued *by* reconciliation can
     * never loop back into reconciliation.
     */
    private suspend fun patch(update: UserSettingsUpdate): ApiResult<UserSettings> =
        when (val result = safeApiCall(json) { api.updateProfile(update.toRequest()).userOrSelf }) {
            is ApiResult.Success -> {
                // The endpoint echoes the full updated user; if a thin body comes
                // back instead, re-read so the cache still holds server truth.
                val dto = result.data
                if (dto != null) ApiResult.Success(publish(dto.toUserSettings())) else fetch()
            }
            is ApiResult.Failure -> result
        }

    /**
     * Brings the device's appearance and the account's `theme` back into agreement and
     * answers with the settings that hold afterwards (a successful push re-reads them).
     *
     * A failed push is swallowed on purpose: the refresh that triggered it still
     * succeeded, and the choice stays marked unsynced so the next refresh tries again.
     */
    private suspend fun reconcileTheme(settings: UserSettings): UserSettings =
        when (
            val outcome = reconcileTheme(
                local = themeStore.themeMode.value,
                hasUnsyncedLocalChange = themeStore.hasUnsyncedChange,
                accountTheme = settings.theme,
            )
        ) {
            ThemeReconciliation.InSync -> settings

            is ThemeReconciliation.AdoptAccount -> {
                themeStore.setSyncedThemeMode(outcome.mode)
                settings
            }

            is ThemeReconciliation.PushLocal ->
                when (val pushed = patch(UserSettingsUpdate(theme = outcome.mode.wire))) {
                    is ApiResult.Success -> {
                        themeStore.setSyncedThemeMode(outcome.mode)
                        pushed.data
                    }
                    is ApiResult.Failure -> settings
                }
        }

    private fun publish(settings: UserSettings): UserSettings {
        cached.value = settings
        trayLimitStore.publish(settings.notificationTrayLimit)
        return settings
    }
}
