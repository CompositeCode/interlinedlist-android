package com.interlinedlist.android.core.materialize.di

import com.interlinedlist.android.core.materialize.data.DefaultMaterializeRepository
import com.interlinedlist.android.core.materialize.data.MaterializeRepository
import com.interlinedlist.android.core.materialize.data.remote.MaterializeApi
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
abstract class MaterializeRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindMaterializeRepository(
        impl: DefaultMaterializeRepository,
    ): MaterializeRepository
}

/** Provides the materialize API off the shared authed Retrofit. */
@Module
@InstallIn(SingletonComponent::class)
object MaterializeDataModule {

    @Provides
    @Singleton
    fun provideMaterializeApi(retrofit: Retrofit): MaterializeApi =
        retrofit.create(MaterializeApi::class.java)
}
