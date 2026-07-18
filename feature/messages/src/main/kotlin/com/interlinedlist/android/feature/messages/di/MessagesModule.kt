package com.interlinedlist.android.feature.messages.di

import android.content.Context
import androidx.room.Room
import com.interlinedlist.android.feature.messages.data.DefaultMessagesRepository
import com.interlinedlist.android.feature.messages.data.MessagesRepository
import com.interlinedlist.android.feature.messages.data.local.MessageDao
import com.interlinedlist.android.feature.messages.data.local.MessagesDatabase
import com.interlinedlist.android.feature.messages.data.remote.MessagesApi
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/** Binds the Messages repository interface to its implementation. */
@Module
@InstallIn(SingletonComponent::class)
abstract class MessagesRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindMessagesRepository(impl: DefaultMessagesRepository): MessagesRepository
}

/**
 * Provides the Messages data layer: a Retrofit API from the shared, authenticated
 * [Retrofit] singleton, and this module's own Room cache (a distinct db file from
 * `:core:database`).
 */
@Module
@InstallIn(SingletonComponent::class)
object MessagesDataModule {

    @Provides
    @Singleton
    fun provideMessagesApi(retrofit: Retrofit): MessagesApi =
        retrofit.create(MessagesApi::class.java)

    @Provides
    @Singleton
    fun provideMessagesDatabase(@ApplicationContext context: Context): MessagesDatabase =
        Room.databaseBuilder(
            context,
            MessagesDatabase::class.java,
            "interlinedlist-messages.db",
        )
            // Disposable cache during early development; real migrations come later.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideMessageDao(db: MessagesDatabase): MessageDao = db.messageDao()
}
