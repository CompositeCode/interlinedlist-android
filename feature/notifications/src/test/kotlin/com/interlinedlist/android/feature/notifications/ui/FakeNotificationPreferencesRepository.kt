package com.interlinedlist.android.feature.notifications.ui

import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.notifications.data.NotificationPreferencesRepository
import com.interlinedlist.android.feature.notifications.domain.NotificationChannel
import com.interlinedlist.android.feature.notifications.domain.NotificationPreference

/**
 * Configurable in-memory [NotificationPreferencesRepository] for ViewModel tests.
 * The GET result and each PATCH result can be pre-set to drive success/failure paths,
 * and every [updatePreference] call is recorded for assertions.
 */
class FakeNotificationPreferencesRepository : NotificationPreferencesRepository {

    var getResult: ApiResult<List<NotificationPreference>> = ApiResult.Success(emptyList())
    var updateResult: ApiResult<Unit> = ApiResult.Success(Unit)

    var getCount = 0
    val updated = mutableListOf<NotificationPreference>()

    override suspend fun getPreferences(): ApiResult<List<NotificationPreference>> {
        getCount++
        return getResult
    }

    override suspend fun updatePreference(preference: NotificationPreference): ApiResult<Unit> {
        updated += preference
        return updateResult
    }
}

/** Builds a sample [NotificationPreference] for tests. */
fun samplePreference(
    key: String = "dig",
    label: String = "Digs on your messages",
    description: String = "When someone digs your message.",
    channels: Map<NotificationChannel, Boolean> = mapOf(
        NotificationChannel.PUSH to true,
        NotificationChannel.IN_APP to false,
    ),
) = NotificationPreference(
    key = key,
    label = label,
    description = description,
    channels = channels,
)
