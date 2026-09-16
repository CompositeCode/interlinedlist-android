package com.interlinedlist.android.core.appsettings

import android.content.SharedPreferences
import com.interlinedlist.android.core.appsettings.data.AppSettingsRepository
import com.interlinedlist.android.core.appsettings.device.AppDeviceStore
import com.interlinedlist.android.core.appsettings.domain.AppSettingsSeed
import com.interlinedlist.android.core.appsettings.domain.RegisteredDevice
import com.interlinedlist.android.core.common.device.DeviceLabelProvider
import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import kotlinx.coroutines.CoroutineDispatcher

/** DispatcherProvider that runs everything on the supplied test dispatcher. */
class TestDispatcherProvider(private val dispatcher: CoroutineDispatcher) : DispatcherProvider {
    override val io: CoroutineDispatcher get() = dispatcher
    override val default: CoroutineDispatcher get() = dispatcher
    override val main: CoroutineDispatcher get() = dispatcher
}

/** A fixed label, standing in for the Build.MODEL-derived one. */
class FakeDeviceLabelProvider(
    override val deviceLabel: String = "InterlinedList Android · Pixel 8",
) : DeviceLabelProvider

/** Records every registry call so the lifecycle can be asserted without a server. */
class FakeAppSettingsRepository : AppSettingsRepository {

    var registerResult: ApiResult<RegisteredDevice> = ApiResult.Success(
        RegisteredDevice(
            deviceId = "android-fake",
            deviceName = "InterlinedList Android · Pixel 8",
            platform = "android",
            isDefault = true,
            lastSeenAt = null,
            appVersion = null,
            osVersion = null,
        ),
    )
    var bootstrapResult: ApiResult<AppSettingsSeed> =
        ApiResult.Failure(AppError.NotFound("Not found"))
    var deregisterResult: ApiResult<Unit> = ApiResult.Success(Unit)

    val registrations = mutableListOf<Registration>()
    val bootstraps = mutableListOf<String>()
    val deregistrations = mutableListOf<String>()

    data class Registration(
        val deviceId: String,
        val deviceName: String,
        val appVersion: String?,
        val osVersion: String?,
    )

    override suspend fun registerDevice(
        deviceId: String,
        deviceName: String,
        appVersion: String?,
        osVersion: String?,
    ): ApiResult<RegisteredDevice> {
        registrations += Registration(deviceId, deviceName, appVersion, osVersion)
        return registerResult
    }

    override suspend fun deregisterDevice(deviceId: String): ApiResult<Unit> {
        deregistrations += deviceId
        return deregisterResult
    }

    override suspend fun bootstrap(deviceId: String): ApiResult<AppSettingsSeed> {
        bootstraps += deviceId
        return bootstrapResult
    }
}

/** In-memory [AppDeviceStore] with the same id-on-first-use behaviour. */
class FakeAppDeviceStore(private val id: String = "android-fake") : AppDeviceStore {
    var generatedIds = 0
        private set
    private var storedId: String? = null

    override fun deviceId(): String = storedId ?: id.also {
        storedId = it
        generatedIds++
    }

    override var hasBootstrapped: Boolean = false
    override var pendingSeed: AppSettingsSeed? = null

    override fun clearAccountState() {
        pendingSeed = null
        hasBootstrapped = false
    }
}

/** Minimal in-memory [SharedPreferences], as used by the auth and messages tests. */
class InMemorySharedPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    override fun getString(key: String?, defValue: String?): String? =
        (values[key] as? String) ?: defValue

    override fun contains(key: String?): Boolean = values.containsKey(key)
    override fun getAll(): MutableMap<String, *> = values
    override fun getInt(key: String?, defValue: Int): Int = (values[key] as? Int) ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = (values[key] as? Long) ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float =
        (values[key] as? Float) ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        (values[key] as? Boolean) ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (values[key] as? MutableSet<String>) ?: defValues

    override fun registerOnSharedPreferenceChangeListener(
        l: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        l: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    override fun edit(): SharedPreferences.Editor = Editor()

    private inner class Editor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private var clear = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor =
            apply { pending[key] = value }
        override fun putStringSet(
            key: String,
            values: MutableSet<String>?,
        ): SharedPreferences.Editor = apply { pending[key] = values }
        override fun putInt(key: String, value: Int): SharedPreferences.Editor =
            apply { pending[key] = value }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor =
            apply { pending[key] = value }
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor =
            apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor =
            apply { pending[key] = value }
        override fun remove(key: String): SharedPreferences.Editor =
            apply { pending[key] = REMOVED }
        override fun clear(): SharedPreferences.Editor = apply { clear = true }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clear) values.clear()
            pending.forEach { (k, v) -> if (v === REMOVED) values.remove(k) else values[k] = v }
            pending.clear()
            clear = false
        }
    }

    companion object {
        private val REMOVED = Any()
    }
}
