package com.interlinedlist.android.feature.notifications.data

import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.map
import com.interlinedlist.android.core.network.error.safeApiCall
import com.interlinedlist.android.feature.notifications.data.remote.NotificationPreferencesApi
import com.interlinedlist.android.feature.notifications.data.remote.dto.toDomain
import com.interlinedlist.android.feature.notifications.data.remote.dto.toUpdateDto
import com.interlinedlist.android.feature.notifications.domain.NotificationPreference
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject

class DefaultNotificationPreferencesRepository @Inject constructor(
    private val api: NotificationPreferencesApi,
    private val json: Json,
    private val dispatchers: DispatcherProvider,
) : NotificationPreferencesRepository {

    override suspend fun getPreferences(): ApiResult<List<NotificationPreference>> =
        withContext(dispatchers.io) {
            safeCall { api.getNotificationPreferences() }.map { it.toDomain() }
        }

    override suspend fun updatePreference(preference: NotificationPreference): ApiResult<Unit> =
        withContext(dispatchers.io) {
            safeCall { api.updateNotificationPreference(preference.toUpdateDto()) }
        }

    // --- helpers -----------------------------------------------------------

    private suspend fun <T> safeCall(block: suspend () -> T): ApiResult<T> =
        safeApiCall(json, block)
}
