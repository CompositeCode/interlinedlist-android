package com.interlinedlist.android.feature.auth.data

import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.model.User

/** Authentication and session operations for the app. */
interface AuthRepository {

    /** Whether a bearer token is already persisted. */
    fun isLoggedIn(): Boolean

    /**
     * Logs in by exchanging credentials for a bearer token, then fetching and
     * caching the authenticated user. Leaves no session behind on failure.
     */
    suspend fun login(email: String, password: String): ApiResult<User>

    /** Clears the persisted session and cached user. */
    suspend fun logout()
}
