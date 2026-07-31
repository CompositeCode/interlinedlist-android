package com.interlinedlist.android.feature.notifications.data

import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.notifications.domain.NotificationPreference

/**
 * Access to the recipient's notification preferences (`/api/user/notification-preferences`).
 *
 * Preferences are a small, always-fresh settings surface, so — unlike the notification
 * feed — this is a thin network wrapper with no local cache: [getPreferences] reads
 * the current state and [updatePreference] applies one event's updated channels. The
 * ViewModel owns the optimistic-update/rollback around [updatePreference].
 */
interface NotificationPreferencesRepository {

    /** Fetches the current per-event preferences and their enabled channels. */
    suspend fun getPreferences(): ApiResult<List<NotificationPreference>>

    /** Persists a single event's updated channel map. */
    suspend fun updatePreference(preference: NotificationPreference): ApiResult<Unit>
}
