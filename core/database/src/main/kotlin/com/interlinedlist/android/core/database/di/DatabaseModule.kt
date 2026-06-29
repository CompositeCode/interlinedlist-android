package com.interlinedlist.android.core.database.di

import android.content.Context
import androidx.room.Room
import com.interlinedlist.android.core.database.InterlinedListDatabase
import com.interlinedlist.android.core.database.dao.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): InterlinedListDatabase =
        Room.databaseBuilder(
            context,
            InterlinedListDatabase::class.java,
            "interlinedlist.db",
        )
            // Disposable cache during early development; replaced with real
            // migrations before the schema carries irreplaceable local data.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideUserDao(db: InterlinedListDatabase): UserDao = db.userDao()
}
