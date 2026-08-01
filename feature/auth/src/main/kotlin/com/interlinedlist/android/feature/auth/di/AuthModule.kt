package com.interlinedlist.android.feature.auth.di

import com.interlinedlist.android.feature.auth.data.AuthRepository
import com.interlinedlist.android.feature.auth.data.DefaultAuthRepository
import com.interlinedlist.android.feature.auth.data.remote.AuthApi
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: DefaultAuthRepository): AuthRepository
}

/**
 * Provides the module-local [AuthApi] from the shared, already-configured
 * [Retrofit] singleton (base URL + auth interceptor) owned by `:core:network`,
 * keeping the account-lifecycle endpoints self-contained within `:feature:auth`.
 */
@Module
@InstallIn(SingletonComponent::class)
object AuthApiModule {

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi =
        retrofit.create(AuthApi::class.java)
}
