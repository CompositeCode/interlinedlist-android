package com.interlinedlist.android.feature.profile.di

import android.content.Context
import androidx.room.Room
import com.interlinedlist.android.feature.profile.data.DefaultProfileRepository
import com.interlinedlist.android.feature.profile.data.ProfileRepository
import com.interlinedlist.android.feature.profile.data.local.ProfileDao
import com.interlinedlist.android.feature.profile.data.local.ProfileDatabase
import com.interlinedlist.android.feature.profile.data.remote.ProfileApi
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/** Binds the repository interface to its default implementation. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ProfileRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindProfileRepository(impl: DefaultProfileRepository): ProfileRepository
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
