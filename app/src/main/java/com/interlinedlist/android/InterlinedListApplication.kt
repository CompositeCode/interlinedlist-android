package com.interlinedlist.android

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point; bootstraps the Hilt dependency graph.
 *
 * Also supplies the WorkManager [Configuration] on demand (paired with removing the
 * default `androidx.startup` WorkManager initializer in the manifest) so `@HiltWorker`
 * instances — such as the documents delta-sync worker — can be constructed by Hilt.
 */
@HiltAndroidApp
class InterlinedListApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
