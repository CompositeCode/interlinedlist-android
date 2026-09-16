package com.interlinedlist.android.feature.notifications.data.remote

import com.interlinedlist.android.feature.notifications.data.remote.dto.PushRegistrationRequest
import com.interlinedlist.android.feature.notifications.data.remote.dto.PushRegistrationResponse
import com.interlinedlist.android.feature.notifications.data.remote.dto.PushUnregisterRequest
import retrofit2.http.Body
import retrofit2.http.HTTP
import retrofit2.http.POST

/**
 * Retrofit description of the device-token endpoints. Provided from the shared,
 * already-authenticated [retrofit2.Retrofit] (base URL + Bearer interceptor), so both
 * calls carry the current session's token — which is what binds a device token to an
 * account, and why [unregister] must run BEFORE the session is cleared on sign-out.
 */
interface PushApi {

    /** Registers (or updates) this device's push token for the signed-in user. */
    @POST("api/push/register")
    suspend fun register(@Body body: PushRegistrationRequest): PushRegistrationResponse

    /**
     * Removes this device's registration. `@HTTP(hasBody = true)` rather than `@DELETE`
     * because Retrofit's `@DELETE` forbids a body and the server expects the token in one.
     */
    @HTTP(method = "DELETE", path = "api/push/unregister", hasBody = true)
    suspend fun unregister(@Body body: PushUnregisterRequest)
}
