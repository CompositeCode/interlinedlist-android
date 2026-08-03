package com.interlinedlist.android.feature.notifications.di

import com.interlinedlist.android.feature.notifications.data.DefaultNotificationPreferencesRepository
import com.interlinedlist.android.feature.notifications.data.NotificationPreferencesRepository
import com.interlinedlist.android.feature.notifications.data.remote.NotificationPreferencesApi
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/** Binds the notification-preferences repository interface to its implementation. */
@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationPreferencesRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindNotificationPreferencesRepository(
        impl: DefaultNotificationPreferencesRepository,
    ): NotificationPreferencesRepository
}

/**
 * Provides the notification-preferences data layer: a Retrofit API built from the
 * shared, authenticated [Retrofit] singleton (base URL + Bearer interceptor).
 */
@Module
@InstallIn(SingletonComponent::class)
object NotificationPreferencesDataModule {

    @Provides
    @Singleton
    fun provideNotificationPreferencesApi(retrofit: Retrofit): NotificationPreferencesApi =
        retrofit.create(NotificationPreferencesApi::class.java)
}
