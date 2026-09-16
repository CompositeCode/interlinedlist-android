package com.interlinedlist.android.feature.profile.data

import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.core.network.error.safeApiCall
import com.interlinedlist.android.core.network.preferences.NotificationTrayLimitStore
import com.interlinedlist.android.feature.profile.data.mapper.toRequest
import com.interlinedlist.android.feature.profile.data.mapper.toUserSettings
import com.interlinedlist.android.feature.profile.data.remote.ProfileApi
import com.interlinedlist.android.feature.profile.domain.UserSettings
import com.interlinedlist.android.feature.profile.domain.UserSettingsUpdate
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
 */
@Singleton
class DefaultSettingsRepository @Inject constructor(
    private val api: ProfileApi,
    private val trayLimitStore: NotificationTrayLimitStore,
    private val json: Json,
    private val dispatchers: DispatcherProvider,
) : SettingsRepository {

    private val cached = MutableStateFlow<UserSettings?>(null)

    override fun observeSettings(): Flow<UserSettings?> = cached.asStateFlow()

    override suspend fun refresh(): ApiResult<UserSettings> = withContext(dispatchers.io) {
        when (val result = safeApiCall(json) { api.getCurrentUser().userOrSelf }) {
            is ApiResult.Success -> {
                val dto = result.data
                    ?: return@withContext ApiResult.Failure(AppError.Unknown("No user in response"))
                ApiResult.Success(publish(dto.toUserSettings()))
            }
            is ApiResult.Failure -> result
        }
    }

    override suspend fun update(update: UserSettingsUpdate): ApiResult<UserSettings> =
        withContext(dispatchers.io) {
            when (val result = safeApiCall(json) { api.updateProfile(update.toRequest()).userOrSelf }) {
                is ApiResult.Success -> {
                    // The endpoint echoes the full updated user; if a thin body comes
                    // back instead, re-read so the cache still holds server truth.
                    val dto = result.data
                    if (dto != null) ApiResult.Success(publish(dto.toUserSettings())) else refresh()
                }
                is ApiResult.Failure -> result
            }
        }

    private fun publish(settings: UserSettings): UserSettings {
        cached.value = settings
        trayLimitStore.publish(settings.notificationTrayLimit)
        return settings
    }
}
