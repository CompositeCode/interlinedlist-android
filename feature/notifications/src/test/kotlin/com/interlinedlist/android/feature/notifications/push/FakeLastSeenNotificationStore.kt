package com.interlinedlist.android.feature.notifications.push

/** In-memory [LastSeenNotificationStore] for unit tests (no Android dependency). */
class FakeLastSeenNotificationStore(
    initial: String? = null,
) : LastSeenNotificationStore {

    private var lastSeen: String? = initial
    private var seenAny: Boolean = initial != null

    override fun lastSeenId(): String? = lastSeen

    override fun setLastSeenId(id: String) {
        lastSeen = id
        seenAny = true
    }

    override fun hasSeenAny(): Boolean = seenAny
}
