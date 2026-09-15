package com.interlinedlist.android.feature.notifications.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService

/**
 * Registers the app's system notification channels (one per [NotificationCategory])
 * on Android O+. Idempotent — creating a channel that already exists is a no-op, and
 * pre-O is skipped entirely (channels don't exist there). Safe to call at every app
 * start.
 *
 * The decision of WHICH channels to register (their ids/names) is the pure
 * [NotificationCategory] enum, which is unit-tested; this helper is the thin Android
 * side-effect that installs them.
 */
object SystemNotificationChannels {

    /** Creates every category channel. No-op below Android O. */
    fun ensureRegistered(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService<NotificationManager>() ?: return
        NotificationCategory.entries.forEach { category ->
            val channel = NotificationChannel(
                category.channelId,
                category.channelName,
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = category.channelDescription
            }
            manager.createNotificationChannel(channel)
        }
    }
}
