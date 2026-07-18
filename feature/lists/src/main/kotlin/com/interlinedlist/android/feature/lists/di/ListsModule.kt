package com.interlinedlist.android.feature.lists.di

import android.content.Context
import androidx.room.Room
import com.interlinedlist.android.feature.lists.data.DefaultListsRepository
import com.interlinedlist.android.feature.lists.data.ListsRepository
import com.interlinedlist.android.feature.lists.data.local.ListDao
import com.interlinedlist.android.feature.lists.data.local.ListsDatabase
import com.interlinedlist.android.feature.lists.data.remote.ListsApi
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
abstract class ListsRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindListsRepository(impl: DefaultListsRepository): ListsRepository
}

/** Provides this module's Retrofit API and its own Room database + DAO. */
@Module
@InstallIn(SingletonComponent::class)
object ListsDataModule {

    @Provides
    @Singleton
    fun provideListsApi(retrofit: Retrofit): ListsApi =
        retrofit.create(ListsApi::class.java)

    @Provides
    @Singleton
    fun provideListsDatabase(@ApplicationContext context: Context): ListsDatabase =
        Room.databaseBuilder(
            context,
            ListsDatabase::class.java,
            "interlinedlist-lists.db",
        )
            // Disposable cache during early development; the cache is re-fetched.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideListDao(db: ListsDatabase): ListDao = db.listDao()
}
