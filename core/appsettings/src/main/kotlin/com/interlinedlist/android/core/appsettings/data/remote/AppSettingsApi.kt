package com.interlinedlist.android.core.appsettings.data.remote

import com.interlinedlist.android.core.appsettings.data.remote.dto.BootstrapResponse
import com.interlinedlist.android.core.appsettings.data.remote.dto.DeregisterDeviceResponse
import com.interlinedlist.android.core.appsettings.data.remote.dto.RegisterDeviceRequest
import com.interlinedlist.android.core.appsettings.data.remote.dto.RegisterDeviceResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The slice of `/help/api/app-settings` this issue needs: the device registry plus the
 * first-run bootstrap read. Built from the shared, already-authenticated Retrofit, so
 * every call carries the session bearer token — which is why deregistration must run
 * *before* the session is cleared on sign-out.
 *
 * Deliberately NOT modelled here: the account/device settings `PUT`s (the sync work,
 * #78) and the device list/rename/promote calls (the Applications screen, #79).
 */
interface AppSettingsApi {

    /**
     * Registers this device, or refreshes an existing registration keyed on
     * `deviceId` (name, versions and `lastSeenAt`; the default flag is preserved).
     * The first device registered for the app becomes the "main workstation".
     */
    @POST("api/user/app-settings/{appKey}/devices")
    suspend fun registerDevice(
        @Path("appKey") appKey: String,
        @Body body: RegisterDeviceRequest,
    ): RegisterDeviceResponse

    /**
     * Deregisters this device and deletes its device-scoped settings document.
     * Returns 404 when the device is already gone, which callers treat as success.
     */
    @DELETE("api/user/app-settings/{appKey}/devices/{deviceId}")
    suspend fun deregisterDevice(
        @Path("appKey") appKey: String,
        @Path("deviceId") deviceId: String,
    ): DeregisterDeviceResponse

    /** Resolves what a brand-new install should start from. 404 means "nothing". */
    @GET("api/user/app-settings/{appKey}/bootstrap")
    suspend fun bootstrap(
        @Path("appKey") appKey: String,
        @Query("deviceId") deviceId: String,
    ): BootstrapResponse
}
