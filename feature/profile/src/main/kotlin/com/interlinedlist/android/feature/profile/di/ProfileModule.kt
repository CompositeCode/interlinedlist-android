package com.interlinedlist.android.feature.profile.di

import android.content.Context
import androidx.room.Room
import com.interlinedlist.android.feature.profile.data.DefaultProfileRepository
import com.interlinedlist.android.feature.profile.data.DefaultSettingsRepository
import com.interlinedlist.android.feature.profile.data.ProfileRepository
import com.interlinedlist.android.feature.profile.data.SettingsRepository
import com.interlinedlist.android.feature.profile.data.local.ProfileDao
import com.interlinedlist.android.feature.profile.data.local.ProfileDatabase
import com.interlinedlist.android.feature.profile.data.location.DeviceLocationSource
import com.interlinedlist.android.feature.profile.data.location.SystemDeviceLocationSource
import com.interlinedlist.android.feature.profile.data.remote.ProfileApi
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/** Binds the repository interfaces to their default implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ProfileRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindProfileRepository(impl: DefaultProfileRepository): ProfileRepository

    /** Singleton so the in-memory settings cache is shared app-wide (Settings + feed). */
    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: DefaultSettingsRepository): SettingsRepository
}

/**
 * Binds the one component allowed to read the device's position, used by the profile
 * location setting. See [DeviceLocationSource] for why it is an interface.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ProfileLocationModule {

    @Binds
    @Singleton
    abstract fun bindDeviceLocationSource(impl: SystemDeviceLocationSource): DeviceLocationSource
}

/** Provides this feature's API, its own Room database, and DAO. */
@Module
@InstallIn(SingletonComponent::class)
object ProfileDataModule {

    @Provides
    @Singleton
    fun provideProfileApi(retrofit: Retrofit): ProfileApi =
        retrofit.create(ProfileApi::class.java)

    @Provides
    @Singleton
    fun provideProfileDatabase(@ApplicationContext context: Context): ProfileDatabase =
        Room.databaseBuilder(
            context,
            ProfileDatabase::class.java,
            "interlinedlist-profile.db",
        )
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideProfileDao(db: ProfileDatabase): ProfileDao = db.profileDao()
}
