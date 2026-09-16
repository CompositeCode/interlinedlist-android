package com.interlinedlist.android.feature.notifications.di

import android.content.Context
import android.content.pm.ApplicationInfo
import com.interlinedlist.android.core.common.session.SessionTeardownTask
import com.interlinedlist.android.feature.notifications.data.DefaultPushRegistrationRepository
import com.interlinedlist.android.feature.notifications.data.PushRegistrationRepository
import com.interlinedlist.android.feature.notifications.data.remote.PushApi
import com.interlinedlist.android.feature.notifications.push.NotificationPermissionChecker
import com.interlinedlist.android.feature.notifications.push.PushEnvironment
import com.interlinedlist.android.feature.notifications.push.PushTokenProvider
import com.interlinedlist.android.feature.notifications.push.PushTokenSessionTeardown
import com.interlinedlist.android.feature.notifications.push.SystemNotificationPermissionChecker
import com.interlinedlist.android.feature.notifications.push.UnavailablePushTokenProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import retrofit2.Retrofit
import javax.inject.Singleton

/** Binds the push device-token lifecycle collaborators to their implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class PushRegistrationModule {

    @Binds
    @Singleton
    abstract fun bindPushRegistrationRepository(
        impl: DefaultPushRegistrationRepository,
    ): PushRegistrationRepository

    /**
     * TODO(#45/#47): swap for the FCM-backed provider once `google-services.json`
     * exists. Until then no token is available and nothing registers — by design.
     */
    @Binds
    @Singleton
    abstract fun bindPushTokenProvider(impl: UnavailablePushTokenProvider): PushTokenProvider

    /**
     * Contributes the push-token unregister to `:feature:auth`'s sign-out teardown, so
     * signing out (or deleting the account) always retires this device's registration.
     */
    @Binds
    @IntoSet
    abstract fun bindPushTokenSessionTeardown(impl: PushTokenSessionTeardown): SessionTeardownTask
}

/** Provides the push data layer and the build-derived registration environment. */
@Module
@InstallIn(SingletonComponent::class)
object PushDataModule {

    @Provides
    @Singleton
    fun providePushApi(retrofit: Retrofit): PushApi = retrofit.create(PushApi::class.java)

    /**
     * Derives `environment` from the build type: the installed app's debuggable flag is
     * the debug/release signal that is visible from a library module at runtime (see
     * [PushEnvironment] for why `BuildConfig.DEBUG` is the wrong one here).
     */
    @Provides
    @Singleton
    fun providePushEnvironment(@ApplicationContext context: Context): PushEnvironment =
        PushEnvironment.fromDebuggable(
            (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0,
        )

    @Provides
    @Singleton
    fun provideNotificationPermissionChecker(
        @ApplicationContext context: Context,
    ): NotificationPermissionChecker = SystemNotificationPermissionChecker(context)
}
