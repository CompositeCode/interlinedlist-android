package com.interlinedlist.android.core.datastore

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** The user's chosen app appearance. [SYSTEM] follows the OS light/dark setting. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/**
 * Holds the device's appearance preference (System / Light / Dark) and remembers
 * whether that preference has reached the account yet.
 *
 * The preference is *also* an account field (`theme` on `GET /api/user` /
 * `PATCH /api/user/update`), so this store is deliberately the **device's** side of a
 * two-way sync rather than a plain local setting:
 *
 * - [setThemeMode] records a choice the user made *here*. It takes effect
 *   immediately — online or not — and leaves [hasUnsyncedChange] true until the
 *   account confirms it, so a change made offline survives a process death and is
 *   still pushed on the next sync.
 * - [setSyncedThemeMode] records the value the *account* holds, clearing the flag.
 *   It is used both when adopting the account's choice (fresh install, or changed on
 *   the web) and when a push of a local choice succeeds.
 *
 * Modelled behind an interface — like `LastSeenNotificationStore` — so the sync logic
 * that drives it can be unit-tested against an in-memory fake with no Android
 * dependency. The value carries no secrets, so the implementation uses plain
 * SharedPreferences rather than the encrypted session store, and exposes the current
 * mode as a [StateFlow] so the theme recomposes the moment it changes.
 */
interface ThemeSettingsStore {

    /** The appearance currently in force on this device. */
    val themeMode: StateFlow<ThemeMode>

    /**
     * True when [themeMode] holds a choice made on this device that the account has
     * not confirmed yet — the flag that makes an offline change win the next
     * reconciliation instead of being overwritten by the stale account value.
     */
    val hasUnsyncedChange: Boolean

    /** Records a choice made on this device, pending a push to the account. */
    fun setThemeMode(mode: ThemeMode)

    /** Records [mode] as the value the account holds, clearing [hasUnsyncedChange]. */
    fun setSyncedThemeMode(mode: ThemeMode)
}

/** SharedPreferences-backed [ThemeSettingsStore]. */
@Singleton
class SharedPrefsThemeSettingsStore @Inject constructor(
    @ApplicationContext context: Context,
) : ThemeSettingsStore {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(readThemeMode())
    override val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    override val hasUnsyncedChange: Boolean
        get() = prefs.getBoolean(KEY_UNSYNCED, false)

    override fun setThemeMode(mode: ThemeMode) = store(mode, unsynced = true)

    override fun setSyncedThemeMode(mode: ThemeMode) = store(mode, unsynced = false)

    /** Persists [mode] plus its sync state and pushes it to observers immediately. */
    private fun store(mode: ThemeMode, unsynced: Boolean) {
        prefs.edit()
            .putString(KEY_THEME_MODE, mode.name)
            .putBoolean(KEY_UNSYNCED, unsynced)
            .apply()
        _themeMode.value = mode
    }

    private fun readThemeMode(): ThemeMode {
        val stored = prefs.getString(KEY_THEME_MODE, null) ?: return ThemeMode.SYSTEM
        return runCatching { ThemeMode.valueOf(stored) }.getOrDefault(ThemeMode.SYSTEM)
    }

    companion object {
        private const val PREFS_FILE = "il_settings.prefs"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_UNSYNCED = "theme_mode_unsynced"
    }
}
