package com.interlinedlist.android.feature.profile.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.feature.profile.domain.Coordinates
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Platform [DeviceLocationSource], backed by the framework's own [LocationManager] —
 * no Play Services dependency, so the feature works on any device the app runs on.
 *
 * Only **coarse** providers are read. `GPS_PROVIDER` is deliberately absent: it needs
 * `ACCESS_FINE_LOCATION`, which this app does not ask for and does not need, because
 * the stored coordinates drive city-level surfaces (the weather and location widgets)
 * rather than navigation.
 *
 * A reading is a one-shot with a time budget and no subscription: the app never
 * watches the user's position, it answers a button press once and stops.
 */
@Singleton
class SystemDeviceLocationSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) : DeviceLocationSource {

    override fun hasCoarsePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    override suspend fun currentCoordinates(): DeviceLocationResult =
        withContext(dispatchers.io) {
            // Re-checked here and not only in the UI: this is the single point that can
            // read a position, so the guarantee belongs with it.
            if (!hasCoarsePermission()) return@withContext DeviceLocationResult.PermissionMissing

            val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return@withContext DeviceLocationResult.Unavailable
            val provider = manager.coarseProvider()
                ?: return@withContext DeviceLocationResult.Unavailable

            // A fresh fix when the device can manage one inside the budget; otherwise
            // whatever it already knows, which is plenty for a city-level location.
            val fix = withTimeoutOrNull(FIX_TIMEOUT_MILLIS) { manager.awaitLocation(provider) }
                ?: manager.lastKnown(provider)

            fix?.let { DeviceLocationResult.Available(Coordinates(it.latitude, it.longitude)) }
                ?: DeviceLocationResult.Unavailable
        }

    /**
     * The coarse provider to read from, or null when the device has none enabled.
     * `fused` (the platform's own, from Android 12) is preferred, then the network
     * provider, then the passive one — never GPS.
     */
    private fun LocationManager.coarseProvider(): String? = COARSE_PROVIDERS.firstOrNull {
        it in allProviders && runCatching { isProviderEnabled(it) }.getOrDefault(false)
    }

    /** One fix from [provider], or null if the provider reports none. */
    // Permission is verified in currentCoordinates() immediately before this runs.
    @SuppressLint("MissingPermission")
    private suspend fun LocationManager.awaitLocation(provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            LocationManagerCompat.getCurrentLocation(
                this,
                provider,
                signal,
                ContextCompat.getMainExecutor(context),
            ) { location: Location? ->
                if (continuation.isActive) continuation.resume(location)
            }
        }

    /** The provider's last known fix, if it has one and still permits reading it. */
    @SuppressLint("MissingPermission")
    private fun LocationManager.lastKnown(provider: String): Location? =
        runCatching { getLastKnownLocation(provider) }.getOrNull()

    private companion object {
        val COARSE_LOCATION: String = Manifest.permission.ACCESS_COARSE_LOCATION

        /**
         * `LocationManager.FUSED_PROVIDER` is API 31, so its value is spelled out to
         * keep this compiling against minSdk 26; the provider list is checked for it
         * anyway, so an older device simply falls through to the next entry.
         */
        val COARSE_PROVIDERS = listOf(
            "fused",
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )

        /** How long a press of "Use my location" may wait before giving up. */
        const val FIX_TIMEOUT_MILLIS = 10_000L
    }
}
