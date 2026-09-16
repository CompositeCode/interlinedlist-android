package com.interlinedlist.android.feature.notifications.data

import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.map
import com.interlinedlist.android.core.network.error.safeApiCall
import com.interlinedlist.android.feature.notifications.data.remote.PushApi
import com.interlinedlist.android.feature.notifications.data.remote.dto.PushRegistrationRequest
import com.interlinedlist.android.feature.notifications.data.remote.dto.PushUnregisterRequest
import com.interlinedlist.android.feature.notifications.push.PushEnvironment
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * `POST /api/push/register` + `DELETE /api/push/unregister` over the shared authed
 * Retrofit stack. Stateless on purpose: WHEN to call these is the lifecycle's job
 * ([com.interlinedlist.android.feature.notifications.push.PushRegistrationManager]).
 */
class DefaultPushRegistrationRepository @Inject constructor(
    private val api: PushApi,
    private val environment: PushEnvironment,
    private val json: Json,
    private val dispatchers: DispatcherProvider,
) : PushRegistrationRepository {

    override suspend fun register(token: String): ApiResult<Unit> = withContext(dispatchers.io) {
        safeApiCall(json) {
            api.register(
                PushRegistrationRequest(
                    token = token,
                    // The server accepts exactly "ios" or "android".
                    platform = ANDROID_PLATFORM,
                    environment = environment.apiValue,
                ),
            )
        }.map { }
    }

    override suspend fun unregister(token: String): ApiResult<Unit> = withContext(dispatchers.io) {
        safeApiCall(json) { api.unregister(PushUnregisterRequest(token)) }
    }

    private companion object {
        const val ANDROID_PLATFORM = "android"
    }
}
