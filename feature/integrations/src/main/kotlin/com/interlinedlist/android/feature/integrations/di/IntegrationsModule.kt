package com.interlinedlist.android.feature.integrations.di

import android.content.Context
import com.interlinedlist.android.feature.integrations.data.DefaultIntegrationsRepository
import com.interlinedlist.android.feature.integrations.data.ExportFileStore
import com.interlinedlist.android.feature.integrations.data.IntegrationsRepository
import com.interlinedlist.android.feature.integrations.data.remote.IntegrationsApi
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import java.io.File
import javax.inject.Singleton

/** Binds the repository interface to its default implementation. */
@Module
@InstallIn(SingletonComponent::class)
abstract class IntegrationsRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindIntegrationsRepository(impl: DefaultIntegrationsRepository): IntegrationsRepository
}

/** Provides this feature's API off the shared authed Retrofit and the export file store. */
@Module
@InstallIn(SingletonComponent::class)
object IntegrationsDataModule {

    @Provides
    @Singleton
    fun provideIntegrationsApi(retrofit: Retrofit): IntegrationsApi =
        retrofit.create(IntegrationsApi::class.java)

    /**
     * Writes exports into `cache/exports`, which the module's FileProvider
     * (`cache-path name="exports"`) shares with the system share sheet.
     */
    @Provides
    @Singleton
    fun provideExportFileStore(@ApplicationContext context: Context): ExportFileStore =
        object : ExportFileStore {
            override fun exportsDir(): File = File(context.cacheDir, "exports")
        }
}
