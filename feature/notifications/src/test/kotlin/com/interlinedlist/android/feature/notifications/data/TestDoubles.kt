package com.interlinedlist.android.feature.notifications.data

import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher

/** DispatcherProvider that runs everything on the supplied test dispatcher. */
class TestDispatcherProvider(private val dispatcher: CoroutineDispatcher) : DispatcherProvider {
    override val io: CoroutineDispatcher get() = dispatcher
    override val default: CoroutineDispatcher get() = dispatcher
    override val main: CoroutineDispatcher get() = dispatcher
}
