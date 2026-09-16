package com.interlinedlist.android.core.datastore.di

import android.content.Context
import android.content.SharedPreferences
import com.interlinedlist.android.core.common.session.SessionTokenProvider
import com.interlinedlist.android.core.datastore.SessionStore
import com.interlinedlist.android.core.datastore.SharedPrefsThemeSettingsStore
import com.interlinedlist.android.core.datastore.ThemeSettingsStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Provides the encrypted preferences instance backing [SessionStore]. */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    fun provideSessionPrefs(@ApplicationContext context: Context): SharedPreferences =
        SessionStore.createEncryptedPrefs(context)
}

/** Exposes [SessionStore] as the [SessionTokenProvider] the network layer needs. */
@Module
@InstallIn(SingletonComponent::class)
abstract class SessionBindsModule {

    @Binds
    abstract fun bindSessionTokenProvider(impl: SessionStore): SessionTokenProvider

    /**
     * Singleton so the activity's theme observer and the settings sync that writes to
     * it are the same store — otherwise an adopted account theme would not reach the
     * running UI until the process restarted.
     */
    @Binds
    @Singleton
    abstract fun bindThemeSettingsStore(impl: SharedPrefsThemeSettingsStore): ThemeSettingsStore
}
