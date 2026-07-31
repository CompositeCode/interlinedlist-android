package com.interlinedlist.android.feature.notifications.data.remote

import com.interlinedlist.android.feature.notifications.data.remote.dto.NotificationPreferenceUpdateDto
import com.interlinedlist.android.feature.notifications.data.remote.dto.NotificationPreferencesResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH

/**
 * Retrofit description of the notification-preferences endpoints. Provided from the
 * shared, already-authenticated [retrofit2.Retrofit] (base URL + Bearer interceptor),
 * so every call here is authed.
 */
interface NotificationPreferencesApi {

    /** The recipient's per-event notification preferences and enabled channels. */
    @GET("api/user/notification-preferences")
    suspend fun getNotificationPreferences(): NotificationPreferencesResponse

    /** Applies a single event's updated channels (OpenAPI body: `{ key, channels }`). */
    @PATCH("api/user/notification-preferences")
    suspend fun updateNotificationPreference(@Body body: NotificationPreferenceUpdateDto)
}
