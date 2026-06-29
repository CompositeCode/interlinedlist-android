package com.interlinedlist.android

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Application entry point; bootstraps the Hilt dependency graph. */
@HiltAndroidApp
class InterlinedListApplication : Application()
