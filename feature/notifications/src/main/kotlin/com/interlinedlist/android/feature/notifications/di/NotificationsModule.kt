package com.interlinedlist.android.feature.notifications.di

import android.content.Context
import androidx.room.Room
import com.interlinedlist.android.feature.notifications.data.DefaultNotificationsRepository
import com.interlinedlist.android.feature.notifications.data.NotificationsRepository
import com.interlinedlist.android.feature.notifications.data.local.NotificationDao
import com.interlinedlist.android.feature.notifications.data.local.NotificationsDatabase
import com.interlinedlist.android.feature.notifications.data.remote.NotificationsApi
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/** Binds the Notifications repository interface to its implementation. */
@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationsRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindNotificationsRepository(
        impl: DefaultNotificationsRepository,
    ): NotificationsRepository
}

/**
 * Provides the Notifications data layer: a Retrofit API from the shared,
 * authenticated [Retrofit] singleton, and this module's own Room cache (a distinct
 * db file from `:core:database`).
 */
@Module
@InstallIn(SingletonComponent::class)
object NotificationsDataModule {

    @Provides
    @Singleton
    fun provideNotificationsApi(retrofit: Retrofit): NotificationsApi =
        retrofit.create(NotificationsApi::class.java)

    @Provides
    @Singleton
    fun provideNotificationsDatabase(@ApplicationContext context: Context): NotificationsDatabase =
        Room.databaseBuilder(
            context,
            NotificationsDatabase::class.java,
            "interlinedlist-notifications.db",
        )
            // Disposable cache during early development; real migrations come later.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideNotificationDao(db: NotificationsDatabase): NotificationDao = db.notificationDao()
}
