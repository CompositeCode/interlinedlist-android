package com.interlinedlist.android.feature.notifications.push

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the "last-seen" marker for the background notification poll so the
 * worker only raises a system notification for items that arrived since the
 * previous run — never re-notifying on every poll.
 *
 * The marker is the newest notification id the poll has already surfaced. It is a
 * tiny scalar with no secrets, so it lives in plain SharedPreferences (mirroring
 * [com.interlinedlist.android.core.datastore.ThemeSettingsStore]). Modelled behind
 * an interface so the worker's selection logic can be unit-tested with an in-memory
 * fake, with no Android dependency.
 */
interface LastSeenNotificationStore {

    /** The id of the newest notification already surfaced, or null on first run. */
    fun lastSeenId(): String?

    /** Records [id] as the newest notification already surfaced. */
    fun setLastSeenId(id: String)

    /** Whether any marker has been recorded yet (false = first ever poll). */
    fun hasSeenAny(): Boolean
}

/** SharedPreferences-backed [LastSeenNotificationStore]. */
@Singleton
class SharedPrefsLastSeenNotificationStore @Inject constructor(
    @ApplicationContext context: Context,
) : LastSeenNotificationStore {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    override fun lastSeenId(): String? = prefs.getString(KEY_LAST_SEEN_ID, null)

    override fun setLastSeenId(id: String) {
        prefs.edit().putString(KEY_LAST_SEEN_ID, id).apply()
    }

    override fun hasSeenAny(): Boolean = prefs.contains(KEY_LAST_SEEN_ID)

    companion object {
        private const val PREFS_FILE = "il_notifications_poll.prefs"
        private const val KEY_LAST_SEEN_ID = "last_seen_notification_id"
    }
}
