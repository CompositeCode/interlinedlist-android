package com.interlinedlist.android.feature.ai.di

import com.interlinedlist.android.feature.ai.data.AiRepository
import com.interlinedlist.android.feature.ai.data.DefaultAiRepository
import com.interlinedlist.android.feature.ai.data.remote.AiApi
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/** Binds the repository interface to its default implementation. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AiRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAiRepository(impl: DefaultAiRepository): AiRepository
}

/** Provides this feature's API off the shared authed Retrofit. */
@Module
@InstallIn(SingletonComponent::class)
object AiDataModule {

    @Provides
    @Singleton
    fun provideAiApi(retrofit: Retrofit): AiApi = retrofit.create(AiApi::class.java)
}
