package com.interlinedlist.android.feature.notifications.domain

/**
 * A single notification-preference event and the delivery channels the recipient
 * has enabled for it. Normalised from `GET /api/user/notification-preferences` into
 * a platform-independent domain type (see the DTO mapper).
 *
 * CRITICAL: the SET of available channels VARIES per event — some events support
 * push + in-app, some push + email, some all three, and a few only one. Only the
 * channels actually present on the event carry a meaningful on/off state; the UI
 * must render a toggle only for [availableChannels] and never assume a fixed triple.
 */
data class NotificationPreference(
    /** Stable event identifier (e.g. "dig", "follow", "mention"); the PATCH key. */
    val key: String,
    /** Human-readable event name shown as the row title. */
    val label: String,
    /** One-line explanation of when this event fires, shown under the label. */
    val description: String,
    /** The delivery channels supported by this event, each with its current state. */
    val channels: Map<NotificationChannel, Boolean>,
) {
    /** The channels this event actually offers, in a stable display order. */
    val availableChannels: List<NotificationChannel>
        get() = NotificationChannel.entries.filter { it in channels }

    /** Whether [channel] is currently enabled for this event (false when absent). */
    fun isEnabled(channel: NotificationChannel): Boolean = channels[channel] == true

    /** Returns a copy with [channel] flipped to [enabled]; no-op if unsupported. */
    fun withChannel(channel: NotificationChannel, enabled: Boolean): NotificationPreference =
        if (channel !in channels) this
        else copy(channels = channels + (channel to enabled))
}

/**
 * A delivery channel a notification can be sent through. The wire keys are
 * `push` / `inApp` / `email`; unknown keys are ignored by the mapper so new
 * server channels never crash the screen (they simply don't render until modelled).
 */
enum class NotificationChannel(val wireKey: String) {
    PUSH("push"),
    IN_APP("inApp"),
    EMAIL("email");

    companion object {
        /** Resolves a wire channel key to a [NotificationChannel], or null if unknown. */
        fun fromWire(key: String?): NotificationChannel? =
            entries.firstOrNull { it.wireKey.equals(key?.trim(), ignoreCase = true) }
    }
}
