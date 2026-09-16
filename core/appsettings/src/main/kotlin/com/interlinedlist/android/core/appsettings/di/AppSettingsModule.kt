package com.interlinedlist.android.core.appsettings.di

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.interlinedlist.android.core.appsettings.AppDeviceSessionTeardown
import com.interlinedlist.android.core.appsettings.data.AppSettingsRepository
import com.interlinedlist.android.core.appsettings.data.DefaultAppSettingsRepository
import com.interlinedlist.android.core.appsettings.data.remote.AppSettingsApi
import com.interlinedlist.android.core.appsettings.device.AppDeviceStore
import com.interlinedlist.android.core.appsettings.device.BuildDeviceLabelProvider
import com.interlinedlist.android.core.appsettings.device.SharedPrefsAppDeviceStore
import com.interlinedlist.android.core.appsettings.domain.ClientVersions
import com.interlinedlist.android.core.common.device.DeviceLabelProvider
import com.interlinedlist.android.core.common.session.SessionTeardownTask
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import retrofit2.Retrofit
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Distinguishes this module's plain preferences from the app-wide unqualified
 * [SharedPreferences] binding, which is the *encrypted* session file owned by
 * `:core:datastore`.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppDevicePreferences

/** Binds the companion-app registry collaborators to their implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppSettingsBindsModule {

    @Binds
    @Singleton
    abstract fun bindAppSettingsRepository(
        impl: DefaultAppSettingsRepository,
    ): AppSettingsRepository

    @Binds
    @Singleton
    abstract fun bindAppDeviceStore(impl: SharedPrefsAppDeviceStore): AppDeviceStore

    /**
     * The one device label in the app. `:feature:auth` injects the interface from
     * `:core:common` and gets this implementation, so `sync-token`'s `deviceLabel` and
     * the device registry's `deviceName` are the same string.
     */
    @Binds
    @Singleton
    abstract fun bindDeviceLabelProvider(impl: BuildDeviceLabelProvider): DeviceLabelProvider

    /**
     * Contributes the deregistration to `:feature:auth`'s sign-out teardown, so signing
     * out (or deleting the account) always retires this device's registration — the
     * same multibinding the push-token unregister uses.
     */
    @Binds
    @IntoSet
    abstract fun bindAppDeviceSessionTeardown(impl: AppDeviceSessionTeardown): SessionTeardownTask
}

/** Provides the module-local API, preferences and build-derived client versions. */
@Module
@InstallIn(SingletonComponent::class)
object AppSettingsDataModule {

    @Provides
    @Singleton
    fun provideAppSettingsApi(retrofit: Retrofit): AppSettingsApi =
        retrofit.create(AppSettingsApi::class.java)

    @Provides
    @Singleton
    @AppDevicePreferences
    fun provideAppDevicePreferences(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences(SharedPrefsAppDeviceStore.PREFS_FILE, Context.MODE_PRIVATE)

    /**
     * The installed app's own version (a library module's `BuildConfig` reports the
     * library's, not the app's) and the OS release. Both are optional to the registry,
     * so an unreadable package info degrades to null rather than failing registration.
     */
    @Provides
    @Singleton
    fun provideClientVersions(@ApplicationContext context: Context): ClientVersions =
        ClientVersions(
            appVersion = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull(),
            osVersion = Build.VERSION.RELEASE?.takeIf { it.isNotBlank() },
        )
}
