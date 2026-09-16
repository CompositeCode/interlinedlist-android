package com.interlinedlist.android.core.network.preferences

import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.model.ViewingPreference
import com.interlinedlist.android.core.network.api.InterlinedListApi
import com.interlinedlist.android.core.network.dto.UpdateUserRequest
import com.interlinedlist.android.core.network.error.safeApiCall
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads and writes the account's [ViewingPreference] — the one account field the
 * messages feed has to own, because the feed is what the preference controls.
 *
 * It lives in `:core:network` rather than in a feature module because two features
 * need it and **no feature module in this repo depends on another feature module**:
 * `:feature:messages` reads and writes it from the in-feed switcher, while
 * `:feature:profile`'s Settings screen writes it (among fourteen other fields)
 * through its own `SettingsRepository`.
 *
 * Consequence to be aware of: `:feature:profile`'s `SettingsRepository` and this
 * store are **two paths to the same `viewingPreference` field**, each with its own
 * in-flight state. They should be consolidated into one owner (most likely a shared
 * account-preferences module) once both surfaces have settled.
 */
@Singleton
class ViewingPreferenceStore @Inject constructor(
    private val api: InterlinedListApi,
    private val json: Json,
) {

    /**
     * The preference currently saved on the account, from `GET /api/user`. An
     * absent or unrecognised value resolves to [ViewingPreference.DEFAULT] rather
     * than failing — the feed always needs something to load with.
     */
    suspend fun read(): ApiResult<ViewingPreference> = safeApiCall(json) {
        ViewingPreference.fromWireOrDefault(api.getCurrentUser().user.viewingPreference)
    }

    /**
     * Saves [preference] with a partial `PATCH /api/user/update`, so the web and
     * Android agree on what the feed shows. Returns the value the server reports as
     * saved (falling back to [preference] when the response does not echo it), so a
     * server-side normalisation wins over what the caller asked for.
     */
    suspend fun write(preference: ViewingPreference): ApiResult<ViewingPreference> {
        val request = UpdateUserRequest(viewingPreference = preference.wire)
        return when (val result = safeApiCall(json) { api.updateUser(request) }) {
            is ApiResult.Success ->
                ApiResult.Success(
                    ViewingPreference.fromWire(result.data.savedViewingPreference) ?: preference,
                )
            is ApiResult.Failure -> result
        }
    }
}
