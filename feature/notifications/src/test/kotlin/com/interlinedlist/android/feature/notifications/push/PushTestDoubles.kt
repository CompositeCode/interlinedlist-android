package com.interlinedlist.android.feature.notifications.push

import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.notifications.data.PushRegistrationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Stands in for whatever push SDK eventually supplies the token (#47). [rotate] models
 * both "the token has just become available" and "the token rotated".
 */
class FakePushTokenProvider(initial: String? = null) : PushTokenProvider {
    private val _token = MutableStateFlow(initial)
    override val token: StateFlow<String?> = _token.asStateFlow()

    fun rotate(value: String?) {
        _token.value = value
    }
}

/** Records every register/unregister the lifecycle issues. */
class RecordingPushRegistrationRepository : PushRegistrationRepository {
    val registered = mutableListOf<String>()
    val unregistered = mutableListOf<String>()

    var registerResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var unregisterResult: ApiResult<Unit> = ApiResult.Success(Unit)

    override suspend fun register(token: String): ApiResult<Unit> {
        registered += token
        return registerResult
    }

    override suspend fun unregister(token: String): ApiResult<Unit> {
        unregistered += token
        return unregisterResult
    }
}

/** Permission checker pinned to a fixed answer. */
class FakeNotificationPermissionChecker(
    var allowed: Boolean = true,
) : NotificationPermissionChecker {
    override fun canPostNotifications(): Boolean = allowed
}
