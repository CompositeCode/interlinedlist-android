package com.interlinedlist.android.feature.billing.di

import com.interlinedlist.android.feature.billing.data.BillingRepository
import com.interlinedlist.android.feature.billing.data.DefaultBillingRepository
import com.interlinedlist.android.feature.billing.data.remote.BillingApi
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
abstract class BillingRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindBillingRepository(impl: DefaultBillingRepository): BillingRepository
}

/** Provides this feature's API off the shared authed Retrofit. */
@Module
@InstallIn(SingletonComponent::class)
object BillingDataModule {

    @Provides
    @Singleton
    fun provideBillingApi(retrofit: Retrofit): BillingApi =
        retrofit.create(BillingApi::class.java)
}
