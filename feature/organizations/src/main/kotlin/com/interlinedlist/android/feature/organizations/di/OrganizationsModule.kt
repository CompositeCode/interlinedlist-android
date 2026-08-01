package com.interlinedlist.android.feature.organizations.di

import android.content.Context
import androidx.room.Room
import com.interlinedlist.android.feature.organizations.data.DefaultOrganizationsRepository
import com.interlinedlist.android.feature.organizations.data.OrganizationsRepository
import com.interlinedlist.android.feature.organizations.data.local.OrganizationDao
import com.interlinedlist.android.feature.organizations.data.local.OrganizationsDatabase
import com.interlinedlist.android.feature.organizations.data.remote.OrganizationsApi
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/** Binds the repository interface to its implementation. */
@Module
@InstallIn(SingletonComponent::class)
abstract class OrganizationsRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindOrganizationsRepository(impl: DefaultOrganizationsRepository): OrganizationsRepository
}

/** Provides this module's Retrofit API and its own Room database + DAO. */
@Module
@InstallIn(SingletonComponent::class)
object OrganizationsDataModule {

    @Provides
    @Singleton
    fun provideOrganizationsApi(retrofit: Retrofit): OrganizationsApi =
        retrofit.create(OrganizationsApi::class.java)

    @Provides
    @Singleton
    fun provideOrganizationsDatabase(@ApplicationContext context: Context): OrganizationsDatabase =
        Room.databaseBuilder(
            context,
            OrganizationsDatabase::class.java,
            "interlinedlist-organizations.db",
        )
            // Disposable cache during early development; the cache is re-fetched.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideOrganizationDao(db: OrganizationsDatabase): OrganizationDao = db.organizationDao()
}
