package com.interlinedlist.android.feature.auth.di

import com.interlinedlist.android.feature.auth.data.AuthRepository
import com.interlinedlist.android.feature.auth.data.DefaultAuthRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: DefaultAuthRepository): AuthRepository
}
