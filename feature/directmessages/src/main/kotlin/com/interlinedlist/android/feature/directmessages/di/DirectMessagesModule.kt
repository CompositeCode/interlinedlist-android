package com.interlinedlist.android.feature.directmessages.di

import android.content.Context
import androidx.room.Room
import com.interlinedlist.android.core.datastore.SessionStore
import com.interlinedlist.android.feature.directmessages.data.CurrentUserIdProvider
import com.interlinedlist.android.feature.directmessages.data.DefaultDirectMessagesRepository
import com.interlinedlist.android.feature.directmessages.data.DirectMessagesRepository
import com.interlinedlist.android.feature.directmessages.data.local.ConversationDao
import com.interlinedlist.android.feature.directmessages.data.local.DirectMessageDao
import com.interlinedlist.android.feature.directmessages.data.local.DirectMessagesDatabase
import com.interlinedlist.android.feature.directmessages.data.remote.DirectMessagesApi
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * Wires the Direct Messages feature: its Retrofit API built from the shared
 * authed [Retrofit] of `:core:network`, its own Room database
 * (`interlinedlist-dm.db`) and DAOs, and the repository binding.
 */
@Module
@InstallIn(SingletonComponent::class)
object DirectMessagesModule {

    /** Builds the DM API from the shared, already-authenticated Retrofit. */
    @Provides
    @Singleton
    fun provideDirectMessagesApi(retrofit: Retrofit): DirectMessagesApi =
        retrofit.create(DirectMessagesApi::class.java)

    @Provides
    @Singleton
    fun provideDirectMessagesDatabase(
        @ApplicationContext context: Context,
    ): DirectMessagesDatabase =
        Room.databaseBuilder(
            context,
            DirectMessagesDatabase::class.java,
            "interlinedlist-dm.db",
        )
            // Disposable cache during early development; real migrations come once
            // the schema carries irreplaceable local data.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideMessageDao(db: DirectMessagesDatabase): DirectMessageDao = db.messageDao()

    @Provides
    fun provideConversationDao(db: DirectMessagesDatabase): ConversationDao = db.conversationDao()

    /** Adapts the Android-backed [SessionStore] to the module's id contract. */
    @Provides
    @Singleton
    fun provideCurrentUserIdProvider(sessionStore: SessionStore): CurrentUserIdProvider =
        CurrentUserIdProvider { sessionStore.userId }
}

/** Repository binding kept separate so the object module above stays pure `@Provides`. */
@Module
@InstallIn(SingletonComponent::class)
abstract class DirectMessagesBindsModule {

    @Binds
    @Singleton
    abstract fun bindDirectMessagesRepository(
        impl: DefaultDirectMessagesRepository,
    ): DirectMessagesRepository
}
