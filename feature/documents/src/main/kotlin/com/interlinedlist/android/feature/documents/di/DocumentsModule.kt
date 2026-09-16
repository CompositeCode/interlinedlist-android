package com.interlinedlist.android.feature.documents.di

import android.content.Context
import androidx.room.Room
import com.interlinedlist.android.feature.documents.data.DefaultDocumentsRepository
import com.interlinedlist.android.feature.documents.data.DefaultListSourcesRepository
import com.interlinedlist.android.feature.documents.data.DocumentsRepository
import com.interlinedlist.android.feature.documents.data.ListSourcesRepository
import com.interlinedlist.android.feature.documents.data.local.DocumentDao
import com.interlinedlist.android.feature.documents.data.local.DocumentsDatabase
import com.interlinedlist.android.feature.documents.data.local.FolderDao
import com.interlinedlist.android.feature.documents.data.local.PendingOpDao
import com.interlinedlist.android.feature.documents.data.local.SyncMetaDao
import com.interlinedlist.android.feature.documents.data.remote.DocumentsApi
import com.interlinedlist.android.feature.documents.data.remote.ListSourcesApi
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/** Binds the repository interface to its default implementation. */
@Module
@InstallIn(SingletonComponent::class)
abstract class DocumentsRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindDocumentsRepository(impl: DefaultDocumentsRepository): DocumentsRepository

    @Binds
    @Singleton
    abstract fun bindListSourcesRepository(impl: DefaultListSourcesRepository): ListSourcesRepository
}

/** Provides this feature's API, its own Room database, and DAOs. */
@Module
@InstallIn(SingletonComponent::class)
object DocumentsDataModule {

    @Provides
    @Singleton
    fun provideDocumentsApi(retrofit: Retrofit): DocumentsApi =
        retrofit.create(DocumentsApi::class.java)

    /** Read-only `/api/lists` access for the Powered Document list-source picker. */
    @Provides
    @Singleton
    fun provideListSourcesApi(retrofit: Retrofit): ListSourcesApi =
        retrofit.create(ListSourcesApi::class.java)

    @Provides
    @Singleton
    fun provideDocumentsDatabase(@ApplicationContext context: Context): DocumentsDatabase =
        Room.databaseBuilder(
            context,
            DocumentsDatabase::class.java,
            "interlinedlist-documents.db",
        )
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideDocumentDao(db: DocumentsDatabase): DocumentDao = db.documentDao()

    @Provides
    fun provideFolderDao(db: DocumentsDatabase): FolderDao = db.folderDao()

    @Provides
    fun providePendingOpDao(db: DocumentsDatabase): PendingOpDao = db.pendingOpDao()

    @Provides
    fun provideSyncMetaDao(db: DocumentsDatabase): SyncMetaDao = db.syncMetaDao()
}
