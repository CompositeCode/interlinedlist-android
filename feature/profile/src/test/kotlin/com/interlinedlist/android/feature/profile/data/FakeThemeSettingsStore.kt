package com.interlinedlist.android.feature.profile.data

import com.interlinedlist.android.core.datastore.ThemeMode
import com.interlinedlist.android.core.datastore.ThemeSettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * In-memory [ThemeSettingsStore] with the same semantics as the SharedPreferences
 * one: [setThemeMode] marks the value unsynced, [setSyncedThemeMode] clears the flag.
 *
 * @param initial the appearance already stored on the "device".
 * @param unsynced whether [initial] is a choice the account has not confirmed —
 *   i.e. what survives a process death after a change made offline.
 */
class FakeThemeSettingsStore(
    initial: ThemeMode = ThemeMode.SYSTEM,
    unsynced: Boolean = false,
) : ThemeSettingsStore {

    private val mode = MutableStateFlow(initial)

    override val themeMode: StateFlow<ThemeMode> = mode

    override var hasUnsyncedChange: Boolean = unsynced
        private set

    override fun setThemeMode(mode: ThemeMode) {
        this.mode.value = mode
        hasUnsyncedChange = true
    }

    override fun setSyncedThemeMode(mode: ThemeMode) {
        this.mode.value = mode
        hasUnsyncedChange = false
    }
}
