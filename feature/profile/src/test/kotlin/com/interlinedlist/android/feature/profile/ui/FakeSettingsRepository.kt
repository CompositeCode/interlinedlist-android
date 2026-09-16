package com.interlinedlist.android.feature.profile.ui

import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.profile.data.SettingsRepository
import com.interlinedlist.android.feature.profile.domain.UserSettings
import com.interlinedlist.android.feature.profile.domain.UserSettingsUpdate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [SettingsRepository] for ViewModel tests. [refreshResult] and
 * [updateResult] drive the success/failure paths; on success the fake behaves like
 * the real one — it publishes the new settings to [observeSettings] — and every
 * update is recorded so tests can assert exactly which fields were sent.
 */
class FakeSettingsRepository : SettingsRepository {

    private val settings = MutableStateFlow<UserSettings?>(null)

    var refreshResult: ApiResult<UserSettings> = ApiResult.Success(UserSettings())
    var updateResult: ((UserSettingsUpdate) -> ApiResult<UserSettings>)? = null

    var refreshCount = 0
    val updates = mutableListOf<UserSettingsUpdate>()

    override fun observeSettings(): Flow<UserSettings?> = settings

    override suspend fun refresh(): ApiResult<UserSettings> {
        refreshCount++
        return refreshResult.also { if (it is ApiResult.Success) settings.value = it.data }
    }

    override suspend fun update(update: UserSettingsUpdate): ApiResult<UserSettings> {
        updates += update
        // By default the server accepts the change and echoes the merged settings.
        val result = updateResult?.invoke(update) ?: ApiResult.Success(merge(update))
        if (result is ApiResult.Success) settings.value = result.data
        return result
    }

    /** Applies the touched fields of [update] to the current settings, as the API would. */
    private fun merge(update: UserSettingsUpdate): UserSettings {
        val current = settings.value ?: UserSettings()
        return current.copy(
            viewingPreference = update.viewingPreference ?: current.viewingPreference,
            showPreviews = update.showPreviews ?: current.showPreviews,
            maxMessageLength = update.maxMessageLength ?: current.maxMessageLength,
            defaultPubliclyVisible = update.defaultPubliclyVisible ?: current.defaultPubliclyVisible,
            messagesPerPage = update.messagesPerPage ?: current.messagesPerPage,
            showAdvancedPostSettings =
                update.showAdvancedPostSettings ?: current.showAdvancedPostSettings,
        )
    }
}
